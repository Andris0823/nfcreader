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
    private const val COMMAND_PAGE = 0x04
    private const val ADDRESS_PAGE = 0x05
    private const val SIZE_PAGE = 0x06
    private const val REPLY_PAGE = 0x07
    private const val TRIGGER_PAGE = 0x85
    private const val CMD_READ: Byte = 0x12
    private const val REPLY_ACK: Byte = 0xAC.toByte()
    private const val LOGICAL_REGISTER_LAST_SAMPLE_T = 0x62
    private const val WORD_COUNT_T_AND_H = 0x02
    private const val REPLY_POLL_ATTEMPTS = 5
    private const val REPLY_POLL_DELAY_MS = 120L

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

            mfc.writePage(COMMAND_PAGE, byteArrayOf(transactionId, CMD_READ, 0x00, 0x00))
            mfc.writePage(
                ADDRESS_PAGE,
                byteArrayOf(LOGICAL_REGISTER_LAST_SAMPLE_T.toByte(), 0x00, 0x00, 0x00)
            )
            mfc.writePage(SIZE_PAGE, byteArrayOf(WORD_COUNT_T_AND_H.toByte(), 0x00, 0x00, 0x00))
            mfc.writePage(TRIGGER_PAGE, byteArrayOf(0x01, 0x00, 0x00, 0x00))

            repeat(REPLY_POLL_ATTEMPTS) { attempt ->
                Thread.sleep(REPLY_POLL_DELAY_MS)
                val response = mfc.readPages(REPLY_PAGE) ?: return null
                if (response.size < 8) {
                    Log.e(TAG, "Túl rövid NFC válasz a szenzor olvasáshoz.")
                    return null
                }

                val replyId = response[0]
                val statusCode = response[1]

                if (replyId != transactionId) {
                    if (attempt == REPLY_POLL_ATTEMPTS - 1) {
                        Log.e(TAG, "Nem érkezett válasz a várt tranzakcióazonosítóval.")
                        return null
                    }
                    return@repeat
                }

                if (statusCode != REPLY_ACK) {
                    Log.e(TAG, "CAEN hiba válasz. Státusz: $statusCode")
                    return null
                }

                val tempRaw = readUInt16BigEndian(response, 4)
                val humRaw = readUInt16BigEndian(response, 6)

                if (tempRaw == INVALID_SAMPLE || humRaw == INVALID_SAMPLE) {
                    Log.w(TAG, "Nincs még érvényes rögzített minta a chipen.")
                    return null
                }

                val temperature = if (tempRaw >= 7232) {
                    (tempRaw - 8192) / FIXED_POINT_SCALE
                } else {
                    tempRaw / FIXED_POINT_SCALE
                }

                val humidity = humRaw / FIXED_POINT_SCALE

                return SensorData(temperature, humidity)
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Hiba az NFC szenzor olvasása közben", e)
            return null
        } finally {
            try { mfc.close() } catch (_: Exception) {}
        }
    }

    private fun readUInt16BigEndian(response: ByteArray, startIndex: Int): Int {
        return ((response[startIndex].toInt() and 0xFF) shl 8) or
            (response[startIndex + 1].toInt() and 0xFF)
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
