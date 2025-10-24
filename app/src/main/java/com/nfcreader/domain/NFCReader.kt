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
            // A legutóbbi hőmérséklet és páratartalom adatok a 0x0A blokkban vannak
            // Blokk 0x0A: 4 byte - első 2 byte: hőmérséklet, második 2 byte: páratartalom
            
            val blockAddress = 0x0A.toByte()
            val cmd = byteArrayOf(
                0x02, // Flags (Address flag set)
                0x20, // Read single block command
                blockAddress
            )
            
            val response = nfcV.transceive(cmd)
            
            if (response != null && response.size >= 5) {
                // Response format: [Status byte, 4 data bytes]
                // Skip first byte (status), read next 4 bytes
                
                // Hőmérséklet: byte 1-2 (signed 16-bit, little-endian, 0.01°C felbontás)
                val tempRaw = (response[1].toInt() and 0xFF) or 
                              ((response[2].toInt() and 0xFF) shl 8)
                val tempSigned = if (tempRaw and 0x8000 != 0) {
                    tempRaw - 0x10000
                } else {
                    tempRaw
                }
                val temperature = tempSigned * 0.01
                
                // Páratartalom: byte 3-4 (unsigned 16-bit, little-endian, 0.01% felbontás)
                val humidityRaw = (response[3].toInt() and 0xFF) or 
                                  ((response[4].toInt() and 0xFF) shl 8)
                val humidity = humidityRaw * 0.01
                
                // Validate sensor readings - return null if values are unrealistic
                // Temperature should be between -40°C and +85°C (sensor spec)
                // Humidity should be between 0% and 100%
                val validTemperature = if (temperature in -40.0..85.0) temperature else null
                val validHumidity = if (humidity in 0.0..100.0) humidity else null
                
                Log.d(TAG, "CAEN qLOG - Temperature: $validTemperature °C, Humidity: $validHumidity %")
                Pair(validTemperature, validHumidity)
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
