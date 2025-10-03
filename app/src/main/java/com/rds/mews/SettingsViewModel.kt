package com.rds.mews

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: MewsRepository): ViewModel() {
    val compactTabBar: StateFlow<Boolean> = repository.compactTabBar.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isMonetColors: StateFlow<Boolean> = repository.monetColors.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val currentTheme: StateFlow<String> = repository.currentTheme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")
    val showDates: StateFlow<Boolean> = repository.showDates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val titlesNum: StateFlow<Int> = repository.titlesNum.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20)
    val titlesPeriod: StateFlow<Int> = repository.titlesPeriod.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 24)
    val rssUpdateInterval: StateFlow<Int> = repository.rssUpdateInterval.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)
    val filterTopics: StateFlow<Boolean> = repository.filterTopics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val currentLlm: StateFlow<String> = repository.currentLlmModel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "gemini-2.0-flash")
    val userApi: StateFlow<String> = repository.userApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val defaultApiKey = repository.DEFAULT_GEMINI_API_KEY

    fun setCompactTab(value: Boolean) = viewModelScope.launch { repository.setCompactTab(value) }
    fun setMonetColors(value: Boolean) = viewModelScope.launch { repository.setMonetColors(value) }
    fun setCurrentTheme(value: String) = viewModelScope.launch { repository.setCurrentTheme(value) }
    fun setShowDates(value: Boolean) = viewModelScope.launch { repository.setShowDates(value) }
    fun setTitlesNum(value: Int) = viewModelScope.launch { repository.setTitlesNum(value) }
    fun setTitlesPeriod(value: Int) = viewModelScope.launch { repository.setTitlesPeriod(value) }
    fun setRssUpdateInterval(context: Context, value: Int) {
        viewModelScope.launch {
            repository.setRssUpdateInterval(value)
            scheduleRssUpdate(context, value)
        }
    }
    fun setFilterTopics(value: Boolean) = viewModelScope.launch { repository.setFilterTopics(value) }
    fun setCurrentLlm(value: String) = viewModelScope.launch { repository.setCurrentLlmModel(value) }
    fun setUserGeminiApi(value: String) = viewModelScope.launch { repository.setUserApiKey(value) }
}

class SettingsViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(MewsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}