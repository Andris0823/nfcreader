package com.nfcreader.domain

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.util.Log
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Szenzor adatok (hőmérséklet és páratartalom).
 */
data class SensorData(
    val temperature: Double? = null,
    val humidity: Double? = null
)

/**
 * NFC tag olvasásáért és dekódolásáért felelős utility osztály.
 * Optimalizálva CAEN RFID qLOG RT0013 (NFC) támogatáshoz.
 */
object NFCReader {

    private const val TAG = "NFCReader"
    private const val FIXED_POINT_SCALE = 32.0

    /**
     * Kiterjesztett függvény a ByteArray konvertálására hexadecimális String-re.
     * Publikussá tesszük, hogy a getTagId is gond nélkül elérhesse.
     */
    fun ByteArray.toHexString(): String {
        return joinToString("") { String.format("%02X", it) }
    }

    /**
     * ÚJ FÜGGVÉNY: Az NFC tag egyedi azonosítójának (UID) lekérése Hex string formátumban.
     * Ez javítja ki a MainActivity-ben (130. sor) lévő hibát!
     */
    fun getTagId(tag: Tag): String {
        return tag.id.toHexString()
    }

    /**
     * NFC tag teljes olvasása, több technológia kipróbálásával.
     * Optimalizálva CAEN qLOG RT0013 támogatáshoz.
     */
    fun readTag(tag: Tag): Triple<String, String, SensorData> {
        // Először megpróbáljuk kiolvasni a szenzoradatokat a CAEN specifikus protokollal
        val sensorData = readCAENqLOGSensors(tag) ?: SensorData(null, null)

        // Ezután jön a hagyományos adatolvasás (opcionális, ha van NDEF vagy egyéb adat rajta)
        val data = readMifareUltralight(tag)
            ?: readNdef(tag)
            ?: readNfcA(tag)
            ?: tag.id

        val hexData = data.toHexString()
        val decodedData = decodeHexToUtf8(hexData)

        return Triple(hexData, decodedData, sensorData)
    }

    /**
     * CAEN qLog RT0013 specifikus szenzorolvasás az NFC Exchange Memory-n keresztül.
     */
    fun readCAENqLOGSensors(tag: Tag): SensorData? {
        val mfc = MifareUltralight.get(tag) ?: return null

        try {
            mfc.connect()

            // Transzfer ID (opcionális, legyen 0x01)
            val transactionId: Byte = 0x01

            // 1. COMMAND regiszter (NFC 0x04-es lap) írása: ID, CMD = 0x12 (READ), RFU, RFU
            // A belső logikai regiszterek olvasásához a 0x12 parancskód kell.
            mfc.writePage(0x04, byteArrayOf(transactionId, 0x12, 0x00, 0x00))

            // 2. ADDRESS regiszter (NFC 0x05-ös lap) írása: belső cím = 0x0062 (LAST_SAMPLE_VALUE_T)
            // Big-endian formátumban küldjük a belső memóriacímet
            mfc.writePage(0x05, byteArrayOf(0x00, 0x62, 0x00, 0x00))

            // 3. SIZE regiszter (NFC 0x06-os lap) írása: 2 regisztert kérünk (0x0002 -> 0x62 és 0x63)
            mfc.writePage(0x06, byteArrayOf(0x00, 0x02, 0x00, 0x00))

            // 4. TRIGGER regiszter (NFC 0x85-ös lap) írása: Értéke 0x01 az indításhoz
            mfc.writePage(0x85, byteArrayOf(0x01, 0x00, 0x00, 0x00))

            // 5. Várakozás, amíg a tag belső MCU-ja átmásolja az adatokat a hardveres pufferekből
            Thread.sleep(100)

            // 6. REPLY és DATA kiolvasása.
            // A readPages(0x07) egyszerre 4 lapot (16 bájtot) olvas be: 0x07, 0x08, 0x09, 0x0A lapokat.
            val response = mfc.readPages(0x07)

            // response[0] -> Reply/Status ID (meg kell egyezzen a kiküldött ID-val)
            // response[1] -> STATUS CODE. A doksi szerint 0x00 = SUCCESS (Sikeres végrehajtás)
            if (response[0] == transactionId && response[1] == 0x00.toByte()) {

                // Az adatok a 0x08-as lapon (DATA REG 0) vannak, ami a response tömbben a 4. indextől kezdődik.
                // response[4]-response[5] -> Hőmérséklet nyers adat (0x62 regiszter)
                // response[6]-response[7] -> Páratartalom nyers adat (0x63 regiszter)
                val tempRaw = ((response[4].toInt() and 0xFF) shl 8) or (response[5].toInt() and 0xFF)
                val humRaw = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)

                // Ha nincs érvényes minta rögzítve, a chip 0xFFFF-et ad vissza
                if (tempRaw == 0xFFFF || humRaw == 0xFFFF) {
                    Log.w(TAG, "A szenzor még nem rögzített érvényes mintát.")
                    return null
                }

                // 7. Matematikai dekódolás (8.5 fixpontos formátum)
                // Hőmérséklet konverzió (kezeli a negatív tartományt is a doksi offset képlete alapján)
                val temperature = if (tempRaw >= 7232) {
                    // Negatív tartomány (-30°C és 0°C között) -> Képlet: (Raw - 8192) / 32.0
                    (tempRaw - 8192) / FIXED_POINT_SCALE
                } else {
                    // Pozitív tartomány (0°C és 70°C között) -> Képlet: Raw / 32.0
                    tempRaw / FIXED_POINT_SCALE
                }

                // Páratartalom konverzió -> Képlet: Raw / 32.0
                val humidity = humRaw / FIXED_POINT_SCALE

                return SensorData(temperature, humidity)
            } else {
                Log.e(TAG, "CAEN Tag hibaüzenetet küldött vagy az ID nem stimmel. Status: ${response[1]}")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hiba a CAEN szenzorok olvasása közben", e)
            return null
        } finally {
            try { mfc.close() } catch (e: Exception) {}
        }
    }

    // --- Kiegészítő / fallback olvasási függvények ---

    private fun readMifareUltralight(tag: Tag): ByteArray? {
        val mfc = MifareUltralight.get(tag) ?: return null
        return try {
            mfc.connect()
            // Alapértelmezett fallback olvasás (pl. az első 4 lap)
            mfc.readPages(0)
        } catch (e: IOException) {
            null
        } finally {
            try { mfc.close() } catch (e: Exception) {}
        }
    }

    private fun readNdef(tag: Tag): ByteArray? {
        val ndef = Ndef.get(tag) ?: return null
        return try {
            ndef.connect()
            val msg = ndef.ndefMessage ?: return null
            msg.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            try { ndef.close() } catch (e: Exception) {}
        }
    }

    private fun readNfcA(tag: Tag): ByteArray? {
        val nfcA = NfcA.get(tag) ?: return null
        return try {
            nfcA.connect()
            // Alapvető azonosító visszaadása transceive-en keresztül, ha más nincs
            tag.id
        } catch (e: IOException) {
            null
        } finally {
            try { nfcA.close() } catch (e: Exception) {}
        }
    }

    private fun decodeHexToUtf8(hexString: String): String {
        return try {
            val bytes = ByteArray(hexString.length / 2)
            for (i in bytes.indices) {
                val index = i * 2
                bytes[i] = hexString.substring(index, index + 2).toInt(16).toByte()
            }
            String(bytes, StandardCharsets.UTF_8)
                .replace("\u0000", "")
                .filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?-_:;()[]{}\"'@#$%&*+=<>/\\|" }
                .trim()
        } catch (e: Exception) {
            "Unable to decode"
        }
    }
}