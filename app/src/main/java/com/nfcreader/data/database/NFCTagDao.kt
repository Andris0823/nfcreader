package com.nfcreader.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object az NFC tag adatbázis műveletekhez.
 * Flow használatával reaktív adatfrissítést biztosít.
 */
@Dao
interface NFCTagDao {
    
    /**
     * Az összes beolvasott NFC tag lekérése időrendi sorrendben (legújabb először).
     * Flow-t használ, hogy automatikusan értesítse a megfigyelőket változásokról.
     */
    @Query("SELECT * FROM nfc_tags ORDER BY timestamp DESC")
    fun getAllTags(): Flow<List<NFCTag>>
    
    /**
     * Új NFC tag beszúrása az adatbázisba.
     * Ha már létezik azonos ID-val, felülírja.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: NFCTag)
    
    /**
     * Összes tag törlése az adatbázisból.
     */
    @Query("DELETE FROM nfc_tags")
    suspend fun deleteAllTags()
    
    /**
     * Egy adott tag törlése ID alapján.
     */
    @Query("DELETE FROM nfc_tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: Long)
}
