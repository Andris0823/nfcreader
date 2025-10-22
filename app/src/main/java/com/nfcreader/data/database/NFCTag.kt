package com.nfcreader.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * NFC Tag entitás az adatbázisban.
 * 
 * @property id Automatikusan generált egyedi azonosító
 * @property tagId Az NFC tagUID-je hexadecimális formátumban
 * @property rawData A nyers hexadecimális adat a tagről
 * @property decodedData UTF-8 dekódolt adat
 * @property temperature Hőmérséklet adat (Celsius), ha elérhető
 * @property timestamp A beolvasás időpontja milliszekundumokban
 */
@Entity(tableName = "nfc_tags")
data class NFCTag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tagId: String,
    val rawData: String,
    val decodedData: String,
    val temperature: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)
