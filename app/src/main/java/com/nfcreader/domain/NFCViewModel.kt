package com.nfcreader.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nfcreader.data.database.NFCDatabase
import com.nfcreader.data.database.NFCTag
import com.nfcreader.data.repository.NFCRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel az NFC tag adatok kezeléséhez.
 * MVVM architektúra használatával elválasztja az UI logikát az üzleti logikától.
 * AndroidViewModel használatával hozzáférünk az Application context-hez.
 */
class NFCViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: NFCRepository
    
    /**
     * Az összes beolvasott tag StateFlow-ként.
     * A StateFlow biztosítja, hogy az UI mindig a legfrissebb adatokat jelenítse meg.
     * A stateIn operátorral a Flow-t StateFlow-vá alakítjuk, amely cache-eli az utolsó értéket.
     */
    val allTags: StateFlow<List<NFCTag>>
    
    init {
        val nfcTagDao = NFCDatabase.getDatabase(application).nfcTagDao()
        repository = NFCRepository(nfcTagDao)
        
        // Flow konvertálása StateFlow-vá
        allTags = repository.allTags.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }
    
    /**
     * Új NFC tag hozzáadása az adatbázishoz.
     * A viewModelScope használatával automatikusan törölődik a coroutine, ha a ViewModel megszűnik.
     */
    fun addTag(tagId: String, rawData: String, decodedData: String, temperature: Double? = null) {
        viewModelScope.launch {
            val tag = NFCTag(
                tagId = tagId,
                rawData = rawData,
                decodedData = decodedData,
                temperature = temperature
            )
            repository.insertTag(tag)
        }
    }
    
    /**
     * Összes tag törlése az adatbázisból.
     */
    fun clearAllTags() {
        viewModelScope.launch {
            repository.deleteAllTags()
        }
    }
    
    /**
     * Egy adott tag törlése.
     */
    fun deleteTag(tagId: Long) {
        viewModelScope.launch {
            repository.deleteTag(tagId)
        }
    }
}
