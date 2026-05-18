package com.nfcreader.domain

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcV
import android.util.Log
import java.io.IOException
import java.nio.charset.StandardCharsets

data class SensorData(
    val temperature: Double? = null,
    val humidity: Double? = null
)

object NFCReader {

    private const val TAG = "NFCReader"
    private const val FIXED_POINT_SCALE = 32.0
    private const val INVALID_SAMPLE = 0xFFFF
    private const val LAST_SAMPLE_PAGE = 0x31
    private const val ISO15693_READ_SINGLE_BLOCK = 0x20.toByte()
    private const val ISO15693_FLAGS = 0x02.toByte()
    private const val ISO15693_STATUS_SUCCESS = 0x00.toByte()
    private const val NEGATIVE_TEMPERATURE_THRESHOLD = 7232
    private const val NEGATIVE_TEMPERATURE_OFFSET = 8192

    fun getTagId(tag: Tag): String {
        return tag.id.toHexString()
    }

    fun readTag(tag: Tag): Triple<String, String, SensorData> {
        val sensorData = readCAENqLOGSensors(tag) ?: SensorData(null, null)

        val data = readMifareUltralight(tag)
            ?: readNdef(tag)
            ?: readNfcA(tag)
            ?: tag.id

        val hexData = data.toHexString()
        val decodedData = decodeHexToUtf8(hexData)

        return Triple(hexData, decodedData, sensorData)
    }

    fun readCAENqLOGSensors(tag: Tag): SensorData? {
        return readCAENFromMifareUltralight(tag)
            ?: readCAENFromNfcV(tag)
    }

    private fun readCAENFromMifareUltralight(tag: Tag): SensorData? {
        val mfc = MifareUltralight.get(tag) ?: return null
        try {
            mfc.connect()
            val pageData = mfc.readPages(LAST_SAMPLE_PAGE) ?: return null
            return decodeLatestSample(pageData, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Hiba a CAEN mintaolvasás közben MifareUltralight módban", e)
            return null
        } finally {
            try { mfc.close() } catch (_: Exception) {}
        }
    }

    private fun readCAENFromNfcV(tag: Tag): SensorData? {
        val nfcV = NfcV.get(tag) ?: return null
        return try {
            nfcV.connect()
            val response = nfcV.transceive(
                byteArrayOf(
                    ISO15693_FLAGS,
                    ISO15693_READ_SINGLE_BLOCK,
                    LAST_SAMPLE_PAGE.toByte()
                )
            ) ?: return null

            if (response.isEmpty() || response[0] != ISO15693_STATUS_SUCCESS) {
                Log.e(TAG, "CAEN NfcV olvasási hiba. Státusz: ${response.firstOrNull()}")
                return null
            }

            decodeLatestSample(response, 1)
        } catch (e: Exception) {
            Log.e(TAG, "Hiba a CAEN mintaolvasás közben NfcV módban", e)
            null
        } finally {
            try { nfcV.close() } catch (_: Exception) {}
        }
    }

    private fun decodeLatestSample(bytes: ByteArray, startIndex: Int): SensorData? {
        if (bytes.size < startIndex + 4) {
            return null
        }

        val tempRaw = (bytes[startIndex].toInt() and 0xFF) or
            ((bytes[startIndex + 1].toInt() and 0xFF) shl 8)
        val humRaw = (bytes[startIndex + 2].toInt() and 0xFF) or
            ((bytes[startIndex + 3].toInt() and 0xFF) shl 8)

        if (tempRaw == INVALID_SAMPLE || humRaw == INVALID_SAMPLE) {
            Log.w(TAG, "Nincs még érvényes rögzített minta a chipen.")
            return null
        }

        // A CAEN belső reprezentációja a negatív értékeket 8192-es offsettel tárolja.
        val temperature = if (tempRaw >= NEGATIVE_TEMPERATURE_THRESHOLD) {
            (tempRaw - NEGATIVE_TEMPERATURE_OFFSET) / FIXED_POINT_SCALE
        } else {
            tempRaw / FIXED_POINT_SCALE
        }

        val humidity = humRaw / FIXED_POINT_SCALE

        return SensorData(temperature, humidity)
    }

    private fun readMifareUltralight(tag: Tag): ByteArray? {
        val mfc = MifareUltralight.get(tag) ?: return null
        return try {
            mfc.connect()
            mfc.readPages(0)
        } catch (e: IOException) { null } finally {
            try { mfc.close() } catch (_: Exception) {}
        }
    }

    private fun readNdef(tag: Tag): ByteArray? {
        val ndef = Ndef.get(tag) ?: return null
        return try {
            ndef.connect()
            ndef.ndefMessage?.toByteArray()
        } catch (e: Exception) { null } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }

    private fun readNfcA(tag: Tag): ByteArray? {
        val nfcA = NfcA.get(tag) ?: return null
        return try {
            nfcA.connect()
            tag.id
        } catch (e: IOException) { null } finally {
            try { nfcA.close() } catch (_: Exception) {}
        }
    }

    fun ByteArray.toHexString(): String {
        return joinToString("") { String.format("%02X", it) }
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
        } catch (e: Exception) { "Unable to decode" }
    }
}
