package com.nfcreader.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nfcreader.data.database.NFCDatabase
import com.nfcreader.data.database.NFCTag
import com.nfcreader.data.repository.NFCRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NFCViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NFCRepository
    val allTags: StateFlow<List<NFCTag>>

    init {
        val nfcTagDao = NFCDatabase.getDatabase(application).nfcTagDao()
        repository = NFCRepository(nfcTagDao)
        allTags = repository.allTags.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }
    fun addTag(tagId: String, rawData: String, decodedData: String, temperature: Double?, humidity: Double?) {
        viewModelScope.launch(Dispatchers.IO) {
            val newTag = NFCTag(
                tagId = tagId,
                rawData = rawData,
                decodedData = decodedData,
                temperature = temperature,
                humidity = humidity
            )
            repository.insertTag(newTag)
        }
    }

    fun clearAllTags() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllTags()
        }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTag(tagId)
        }
    }
}