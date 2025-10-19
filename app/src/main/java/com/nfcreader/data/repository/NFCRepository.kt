package com.nfcreader.data.repository

import com.nfcreader.data.database.NFCTag
import com.nfcreader.data.database.NFCTagDao
import kotlinx.coroutines.flow.Flow

/**
 * Repository osztály az NFC tag adatok kezeléséhez.
 * A Repository minta használatával elválasztjuk az adatforrást az üzleti logikától.
 * Ez teszi lehetővé, hogy később könnyen cseréljük az adatforrást (pl. hálózat, cache).
 */
class NFCRepository(private val nfcTagDao: NFCTagDao) {
    
    /**
     * Az összes beolvasott tag lekérése Flow formájában.
     * A Flow automatikusan értesít az UI-t minden adatbázis változásról.
     */
    val allTags: Flow<List<NFCTag>> = nfcTagDao.getAllTags()
    
    /**
     * Új tag mentése az adatbázisba.
     * Suspend függvény, hogy háttérszálon fusson.
     */
    suspend fun insertTag(tag: NFCTag) {
        nfcTagDao.insertTag(tag)
    }
    
    /**
     * Összes tag törlése az adatbázisból.
     */
    suspend fun deleteAllTags() {
        nfcTagDao.deleteAllTags()
    }
    
    /**
     * Egy adott tag törlése.
     */
    suspend fun deleteTag(tagId: Long) {
        nfcTagDao.deleteTag(tagId)
    }
}
