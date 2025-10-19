package com.nfcreader.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room adatbázis az NFC tagek tárolására.
 * Singleton pattern használatával biztosítjuk, hogy csak egy példány létezzen.
 */
@Database(
    entities = [NFCTag::class],
    version = 1,
    exportSchema = false
)
abstract class NFCDatabase : RoomDatabase() {
    
    abstract fun nfcTagDao(): NFCTagDao
    
    companion object {
        @Volatile
        private var INSTANCE: NFCDatabase? = null
        
        /**
         * Adatbázis példány lekérése vagy létrehozása.
         * Thread-safe implementáció double-checked locking használatával.
         */
        fun getDatabase(context: Context): NFCDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NFCDatabase::class.java,
                    "nfc_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
