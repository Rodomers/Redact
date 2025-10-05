package com.rds.mews

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SourcesViewModel(private val repository: MewsRepository): ViewModel() {
    val sources: StateFlow<List<RSS>> = repository.sources
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addSource(context: Context, name: String, link: String) {
        viewModelScope.launch {
            repository.addSource(context, name, link)
        }
    }

    fun deleteSource(name: String) {
        viewModelScope.launch {
            repository.deleteSource(name)
        }
    }

    fun changeSource(oldName: String, newName: String) {
        viewModelScope.launch {
            repository.changeSource(oldName, newName)
        }
    }
}

class SourcesViewModelFactory() : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SourcesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SourcesViewModel(MewsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}