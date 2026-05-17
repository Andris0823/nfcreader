package com.nfcreader.domain

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcV
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
    private const val CAEN_LAST_SAMPLE_BLOCK: Byte = 0x31
    private const val FIXED_POINT_SCALE = 32
    private const val FIXED_POINT_MULTIPLIER = 1.0 / FIXED_POINT_SCALE
    private const val STATUS_ERROR_MASK = 0x01
    private const val INVALID_SAMPLE = 0xFFFF
    // 70°C in fixed-point format (70 * 32 = 2240)
    private const val TEMP_MAX_RAW = 70 * FIXED_POINT_SCALE
    // -30°C in fixed-point (8192 + (-30 * 32)) = 7232.
    private const val TEMP_NEGATIVE_RAW_START = 7232
    // RT0013 uses a +8192 offset for negative temperatures in fixed-point encoding.
    private const val TEMP_NEGATIVE_OFFSET = 8192
    // 100% in fixed-point format (100 * 32 = 3200)
    private const val HUMIDITY_MAX_RAW = 100 * FIXED_POINT_SCALE
    private const val MAX_TEMPERATURE = 70.0
    private const val MAX_HUMIDITY = 100.0
    
    /**
     * NFC tag UID-jének kiolvasása hexadecimális formátumban.
     */
    fun getTagId(tag: Tag): String {
        return tag.id.toHexString()
    }
    
    /**
     * Mifare Ultralight tag adatainak olvasása.
     * A tagről olvassa a teljes használható memóriát.
     */
    fun readMifareUltralight(tag: Tag): ByteArray? {
        val mifareUltralight = MifareUltralight.get(tag)
        return try {
            mifareUltralight.connect()
            val payload = mutableListOf<Byte>()
            
            // Mifare Ultralight általában 16 oldal, oldalanként 4 byte
            // Az első 4 oldal általában gyári adatokat tartalmaz
            // Olvassuk az 4-15 oldalakat (felhasználói adatok)
            for (page in 4..15) {
                try {
                    val pageData = mifareUltralight.readPages(page)
                    // readPages 4 oldalt olvas egyszerre (16 byte)
                    // De csak az első oldalt használjuk fel ismétlődés elkerülésére
                    if (pageData.isNotEmpty()) {
                        payload.addAll(pageData.take(4).toList())
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error reading page $page", e)
                    break
                }
            }
            
            payload.toByteArray()
        } catch (e: IOException) {
            Log.e(TAG, "Error reading Mifare Ultralight", e)
            null
        } finally {
            try {
                mifareUltralight.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing Mifare Ultralight", e)
            }
        }
    }
    
    /**
     * Hőmérséklet és páratartalom leolvasása CAEN qLOG RT0013 tag-ről (NFC).
     * Ez a tag ISO15693/NfcV protokollt használ.
     * 
     * @return Pair of (temperature in °C, humidity in %) or null values if not available
     */
    fun readCAENqLOGSensors(tag: Tag): Pair<Double?, Double?> {
        val nfcV = NfcV.get(tag) ?: return Pair(null, null)
        
        return try {
            nfcV.connect()
            
        // CAEN qLOG RT0013 specifikus memória címek
        // A legutóbbi hőmérséklet és páratartalom adatok a 0x31 blokkban vannak
        // (0x62/0x63 word címek), 4 byte - első 2 byte: hőmérséklet, második 2 byte: páratartalom
        
        val blockAddress = CAEN_LAST_SAMPLE_BLOCK
        val cmd = byteArrayOf(
            0x02, // Flags (Address flag set)
            0x20, // Read single block command
            blockAddress
        )
            
            val response = nfcV.transceive(cmd)
            
        if (response != null && response.size >= 5) {
            // ISO15693 response flags: error is indicated by bit 0.
            if ((response[0].toInt() and STATUS_ERROR_MASK) != 0) {
                Log.w(TAG, "CAEN qLOG response error: ${response[0]}")
                return Pair(null, null)
            }
            // Response format: [Status byte, 4 data bytes]
            // Skip first byte (status), read next 4 bytes
            
            // Hőmérséklet: byte 1-2 (16-bit, big-endian, fixpontos 1/32°C)
            val tempRaw = ((response[1].toInt() and 0xFF) shl 8) or
                          (response[2].toInt() and 0xFF)
            val temperature = decodeTemperature(tempRaw)
            
            // Páratartalom: byte 3-4 (16-bit, big-endian, fixpontos 1/32%)
            val humidityRaw = ((response[3].toInt() and 0xFF) shl 8) or
                              (response[4].toInt() and 0xFF)
            val humidity = decodeHumidity(humidityRaw)
            
            Log.d(TAG, "CAEN qLOG - Temperature: $temperature °C, Humidity: $humidity %")
            Pair(temperature, humidity)
            } else {
                Log.w(TAG, "CAEN qLOG response invalid or too short")
                Pair(null, null)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read CAEN qLOG sensors - tag may not be qLOG RT0013", e)
            Pair(null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading CAEN qLOG sensors", e)
            Pair(null, null)
        } finally {
            try {
                nfcV.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing NfcV", e)
            }
        }
    }

    private fun decodeTemperature(rawValue: Int): Double? {
        if (rawValue == INVALID_SAMPLE) {
            return null
        }
        // RT0013 reference implementation clamps values above the maximum to the max range.
        val value = when {
            rawValue in 0..TEMP_MAX_RAW -> rawValue * FIXED_POINT_MULTIPLIER
            // Values between max and negative-encoding start are reserved in RT0013 and clamped to max per reference.
            rawValue in (TEMP_MAX_RAW + 1) until TEMP_NEGATIVE_RAW_START -> MAX_TEMPERATURE
            // Negative values are encoded as (8192 + value * 32), yielding raw 7232..8191 (-30°C to just below 0°C).
            rawValue in TEMP_NEGATIVE_RAW_START until TEMP_NEGATIVE_OFFSET -> (rawValue - TEMP_NEGATIVE_OFFSET) * FIXED_POINT_MULTIPLIER
            // Values above TEMP_NEGATIVE_OFFSET are invalid/out of range.
            else -> null
        }
        return value
    }

    private fun decodeHumidity(rawValue: Int): Double? {
        if (rawValue == INVALID_SAMPLE) {
            return null
        }
        // RT0013 reference implementation clamps values above the maximum to the max range.
        val value = when {
            rawValue in 0..HUMIDITY_MAX_RAW -> rawValue * FIXED_POINT_MULTIPLIER
            rawValue > HUMIDITY_MAX_RAW -> MAX_HUMIDITY
            // Any other raw value is treated as invalid.
            else -> null
        }
        return value
    }
    
    /**
     * Hőmérséklet leolvasása Mifare Ultralight tag-ről (NTAG21x T variánsok).
     * A hőmérséklet adat általában a 0x29 (41) oldalon található.
     * Fallback funkció, ha a tag nem CAEN qLOG.
     * 
     * @return Hőmérséklet Celsius fokban, vagy null ha nem érhető el
     */
    private fun readTemperatureNTAG(tag: Tag): Double? {
        val mifareUltralight = MifareUltralight.get(tag) ?: return null
        return try {
            mifareUltralight.connect()
            
            // NTAG21x T típusú tagek esetén a hőmérséklet adat a 0x29 (41) oldalon van
            // A formátum: 2 byte előjeles integer (big-endian)
            val temperaturePage = mifareUltralight.readPages(0x29)
            
            if (temperaturePage != null && temperaturePage.size >= 2) {
                // Az első két byte tartalmazza a hőmérséklet adatot
                val tempRaw = ((temperaturePage[0].toInt() and 0xFF) shl 8) or 
                              (temperaturePage[1].toInt() and 0xFF)
                
                // Konvertálás előjeles értékké
                val tempSigned = if (tempRaw and 0x8000 != 0) {
                    tempRaw - 0x10000
                } else {
                    tempRaw
                }
                
                // A hőmérséklet értéke 0.0625 °C léptékű
                val temperature = tempSigned * 0.0625
                
                Log.d(TAG, "NTAG Temperature read: $temperature °C")
                temperature
            } else {
                Log.w(TAG, "Temperature page data not available or invalid")
                null
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read NTAG temperature", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading NTAG temperature", e)
            null
        } finally {
            try {
                mifareUltralight.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing Mifare Ultralight", e)
            }
        }
    }
    
    /**
     * NDEF formátumú tag olvasása.
     */
    fun readNdef(tag: Tag): ByteArray? {
        val ndef = Ndef.get(tag)
        return try {
            ndef?.connect()
            val ndefMessage = ndef?.ndefMessage
            val payload = ndefMessage?.records?.firstOrNull()?.payload
            payload
        } catch (e: IOException) {
            Log.e(TAG, "Error reading NDEF", e)
            null
        } finally {
            try {
                ndef?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing NDEF", e)
            }
        }
    }
    
    /**
     * NfcA technológia használatával olvasás.
     */
    fun readNfcA(tag: Tag): ByteArray? {
        val nfcA = NfcA.get(tag)
        return try {
            nfcA?.connect()
            // NfcA ATQA és SAK adatok
            val atqa = nfcA?.atqa
            val sak = nfcA?.sak?.toByte()
            
            val result = mutableListOf<Byte>()
            atqa?.let { result.addAll(it.toList()) }
            sak?.let { result.add(it) }
            
            if (result.isEmpty()) null else result.toByteArray()
        } catch (e: IOException) {
            Log.e(TAG, "Error reading NfcA", e)
            null
        } finally {
            try {
                nfcA?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing NfcA", e)
            }
        }
    }
    
    /**
     * ByteArray konvertálása hexadecimális string-re.
     */
    fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte ->
            "%02X".format(byte)
        }
    }
    
    /**
     * Hexadecimális string dekódolása UTF-8 szöveggé.
     * Eltávolítja a null karaktereket és a nem nyomtatható karaktereket.
     */
    fun decodeHexToUtf8(hexString: String): String {
        return try {
            val bytes = hexString.chunked(2)
                .mapNotNull { it.toIntOrNull(16)?.toByte() }
                .toByteArray()
            
            // UTF-8 dekódolás és tisztítás
            String(bytes, StandardCharsets.UTF_8)
                .replace("\u0000", "") // Null karakterek eltávolítása
                .filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?-_:;()[]{}\"'@#$%&*+=<>/\\|" }
                .trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding hex to UTF-8", e)
            "Unable to decode"
        }
    }
    
    /**
     * NFC tag teljes olvasása, több technológia kipróbálásával.
     * Optimalizálva CAEN qLOG RT0013 támogatáshoz.
     * Visszaadja a hex adatot, dekódolt adatot és a szenzor adatokat (ha elérhetőek).
     */
    fun readTag(tag: Tag): Triple<String, String, SensorData> {
        // Először próbáljuk a Mifare Ultralight-ot
        val data = readMifareUltralight(tag) 
            ?: readNdef(tag) 
            ?: readNfcA(tag)
            ?: tag.id
        
        val hexData = data.toHexString()
        val decodedData = decodeHexToUtf8(hexData)
        
        // Szenzor adatok olvasása - először CAEN qLOG RT0013-t próbáljuk (prioritás)
        val (temperature, humidity) = readCAENqLOGSensors(tag)
        
        // Ha nem sikerült CAEN qLOG-ként olvasni, próbáljuk NTAG T-ként
        val finalTemperature = temperature ?: readTemperatureNTAG(tag)
        
        val sensorData = SensorData(
            temperature = finalTemperature,
            humidity = humidity
        )
        
        return Triple(hexData, decodedData, sensorData)
    }
}
