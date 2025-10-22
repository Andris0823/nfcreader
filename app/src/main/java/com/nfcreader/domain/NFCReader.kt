package com.nfcreader.domain

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.util.Log
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * NFC tag olvasásáért és dekódolásáért felelős utility osztály.
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
     */
    fun readTag(tag: Tag): Pair<String, String> {
        // Először próbáljuk a Mifare Ultralight-ot
        val data = readMifareUltralight(tag) 
            ?: readNdef(tag) 
            ?: readNfcA(tag)
            ?: tag.id
        
        val hexData = data.toHexString()
        val decodedData = decodeHexToUtf8(hexData)
        
        return Pair(hexData, decodedData)
    }
}
