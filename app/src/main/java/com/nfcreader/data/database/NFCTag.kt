package com.nfcreader.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * NFC Tag entitás az adatbázisban.
 * KIZÁRÓLAG CAEN qLOG RT0013 támogatással.
 * 
 * @property id Automatikusan generált egyedi azonosító
 * @property tagId Az NFC tag UID-je hexadecimális formátumban
 * @property temperature Hőmérséklet adat (Celsius), ha elérhető (CAEN qLOG RT0013)
 * @property humidity Páratartalom adat (%), ha elérhető (CAEN qLOG RT0013)
 * @property timestamp A beolvasás időpontja milliszekundumokban
 */
@Entity(tableName = "nfc_tags")
data class NFCTag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tagId: String,
    val temperature: Double? = null,
    val humidity: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)
