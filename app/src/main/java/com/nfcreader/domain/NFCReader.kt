package com.nfcreader.domain

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
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
    private const val NEGATIVE_TEMPERATURE_THRESHOLD = 7232
    private const val NEGATIVE_TEMPERATURE_OFFSET = 8192
    private const val COMMAND_PAGE = 0x04
    private const val REQUEST_PAGE = 0x05
    private const val LENGTH_PAGE = 0x06
    private const val RESPONSE_PAGE = 0x07
    private const val EXECUTE_PAGE = 0x85
    private const val INTERNAL_LAST_SAMPLE_VALUE_T = 0x62
    private const val INTERNAL_SAMPLE_WORD_COUNT = 0x02
    private const val COMMAND_READ_INTERNAL_MEMORY = 0x12
    private const val COMMAND_STATUS_SUCCESS = 0x00.toByte()

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
        val mfc = MifareUltralight.get(tag) ?: return null

        try {
            mfc.connect()
            val transactionId: Byte = 0x01

            mfc.writePage(
                COMMAND_PAGE,
                byteArrayOf(transactionId, COMMAND_READ_INTERNAL_MEMORY.toByte(), 0x00, 0x00)
            )
            mfc.writePage(
                REQUEST_PAGE,
                byteArrayOf(0x00, INTERNAL_LAST_SAMPLE_VALUE_T.toByte(), 0x00, 0x00)
            )
            mfc.writePage(
                LENGTH_PAGE,
                byteArrayOf(0x00, INTERNAL_SAMPLE_WORD_COUNT.toByte(), 0x00, 0x00)
            )
            mfc.writePage(EXECUTE_PAGE, byteArrayOf(0x01, 0x00, 0x00, 0x00))

            Thread.sleep(120)

            val response = mfc.readPages(RESPONSE_PAGE) ?: return null
            val replyId = response[0]
            val statusCode = response[1]

            if (replyId != transactionId || statusCode != COMMAND_STATUS_SUCCESS) {
                Log.e(TAG, "CAEN hiba válasz. Státusz: $statusCode")
                return null
            }

            return decodeLatestSample(response, 4)
        } catch (e: Exception) {
            Log.e(TAG, "Hiba az NFC szenzor olvasása közben", e)
            return null
        } finally {
            try { mfc.close() } catch (_: Exception) {}
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

        // A CAEN belső reprezentációja a negatív értékeket a NEGATIVE_TEMPERATURE_OFFSET offsettel tárolja.
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
