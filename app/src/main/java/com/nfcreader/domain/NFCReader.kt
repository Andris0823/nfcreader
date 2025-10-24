package com.nfcreader.domain

import android.nfc.Tag
import android.nfc.tech.NfcV
import android.util.Log
import java.io.IOException

/**
 * Szenzor adatok (hőmérséklet és páratartalom).
 */
data class SensorData(
    val temperature: Double? = null,
    val humidity: Double? = null
)

/**
 * NFC tag olvasásáért felelős utility osztály.
 * KIZÁRÓLAG CAEN RFID qLOG RT0013 NFC tag támogatására.
 */
object NFCReader {
    
    private const val TAG = "NFCReader"
    
    // CAEN qLOG RT0013 szenzor specifikációs határok
    private const val MIN_TEMPERATURE = -40.0
    private const val MAX_TEMPERATURE = 85.0
    private const val MIN_HUMIDITY = 0.0
    private const val MAX_HUMIDITY = 100.0
    
    /**
     * NFC tag UID-jének kiolvasása hexadecimális formátumban.
     */
    fun getTagId(tag: Tag): String {
        return tag.id.toHexString()
    }
    
    /**
     * Hőmérséklet és páratartalom leolvasása CAEN qLOG RT0013 tag-ről (NFC).
     * Ez a tag ISO15693/NfcV protokollt használ.
     * 
     * @return SensorData hőmérséklettel és páratartalommal, vagy null értékekkel ha nem elérhető
     */
    fun readCAENqLOGSensors(tag: Tag): SensorData {
        val nfcV = NfcV.get(tag)
        
        if (nfcV == null) {
            Log.w(TAG, "NfcV not available - ez nem CAEN qLOG RT0013 tag")
            return SensorData(null, null)
        }
        
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
                val validTemperature = if (temperature in MIN_TEMPERATURE..MAX_TEMPERATURE) temperature else null
                val validHumidity = if (humidity in MIN_HUMIDITY..MAX_HUMIDITY) humidity else null
                
                Log.d(TAG, "CAEN qLOG - Temperature: $validTemperature °C, Humidity: $validHumidity %")
                SensorData(validTemperature, validHumidity)
            } else {
                Log.w(TAG, "CAEN qLOG response invalid or too short")
                SensorData(null, null)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not read CAEN qLOG sensors", e)
            SensorData(null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading CAEN qLOG sensors", e)
            SensorData(null, null)
        } finally {
            try {
                nfcV.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing NfcV", e)
            }
        }
    }
    
    /**
     * NFC tag teljes olvasása - KIZÁRÓLAG CAEN qLOG RT0013 támogatással.
     * Visszaadja a tag ID-t és a szenzor adatokat (hőmérséklet és páratartalom).
     */
    fun readTag(tag: Tag): Pair<String, SensorData> {
        val tagId = getTagId(tag)
        val sensorData = readCAENqLOGSensors(tag)
        return Pair(tagId, sensorData)
    }
    
    /**
     * ByteArray konvertálása hexadecimális string-re.
     */
    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte ->
            "%02X".format(byte)
        }
    }
}
