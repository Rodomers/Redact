package com.rds.mews.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rds.mews.MainActivity
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.localcore.handleNotificationsPermissionRequest
import com.rds.mews.localcore.isNotificationPermissionGranted
import com.rds.mews.localcore.isScheduleExactAlarm
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: MewsRepository): ViewModel() {
    private val _scrollEvents = Channel<SettingsScrollEvent>(Channel.CONFLATED)
    val scrollEvents = _scrollEvents.receiveAsFlow()

    private val _showAlarmsSheet = MutableStateFlow(false)
    val showAlarmsSheet: StateFlow<Boolean> = _showAlarmsSheet

    private val _showNotificationSheet = MutableStateFlow(false)
    val showNotificationSheet: StateFlow<Boolean> = _showNotificationSheet


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
    val endureTime: StateFlow<Boolean> = repository.enlargedTimestamps.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), false)
    val titlesAlarmUpdate: StateFlow<Boolean> = repository.titlesAlarmUpdate.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), false)
    val titlesAlarmMins: StateFlow<Int> = repository.titlesAlarmTimeMins.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), 540)
    val titlesUpdateFrequency: StateFlow<Int> = repository.titlesAutoUpdateFrequency.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), 24)
    val exactAlarmsAllowed: StateFlow<Boolean> = repository.exactAlarmsAllowed.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), false)
    val proxyEnabled: StateFlow<Boolean> = repository.proxyEnabled.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), false)
    private val _defaultApiKey = repository.DEFAULT_GEMINI_API_KEY
    private val _isKeyDefault = MutableStateFlow(isApiKeyDefault(userApi.value))
    val isKeyDefault: StateFlow<Boolean> = _isKeyDefault
    val bannedNews = repository.bannedNewsFlow

    fun setCompactTab(value: Boolean) = viewModelScope.launch { repository.setCompactTab(value) }
    fun setMonetColors(value: Boolean) = viewModelScope.launch { repository.setMonetColors(value) }
    fun setCurrentTheme(value: String) = viewModelScope.launch { repository.setCurrentTheme(value) }
    fun setShowDates(value: Boolean) = viewModelScope.launch { repository.setShowDates(value) }
    fun setTitlesNum(value: Int) = viewModelScope.launch { repository.setTitlesNum(value) }
    fun setTitlesPeriod(value: Int) = viewModelScope.launch { repository.setTitlesPeriod(value) }
    fun setRssUpdateInterval(context: Context, value: Int) {
        viewModelScope.launch {
            repository.setRssUpdateInterval(context,value)
        }
    }
    fun setFilterTopics(value: Boolean) = viewModelScope.launch { repository.setFilterTopics(value) }
    fun setCurrentLlm(value: String) = viewModelScope.launch { repository.setCurrentLlmModel(value) }
    fun setUserGeminiApi(value: String) = viewModelScope.launch {
        repository.setUserApiKey(value)
        _isKeyDefault.value = isApiKeyDefault(value)
    }
    fun resetApiKey() = viewModelScope.launch {
        repository.setUserApiKey(_defaultApiKey)
        _isKeyDefault.value = true
    }
    fun setEndureTime(value: Boolean) = viewModelScope.launch { repository.setEnlargeTimestamps(value) }
    fun setBannedNews(value: Set<String>) = viewModelScope.launch { repository.setBannedNews(value) }
    fun delBannedNews(value: String) = viewModelScope.launch { repository.delBannedNew(value) }
    fun setTitlesAlarmUpdate(
        context: Context,
        onShowNotificationsSheet: () -> Unit,
        onShowAlarmsSheet: () -> Unit,
        value: Boolean,
        activity: MainActivity
    ) = viewModelScope.launch {
        when {
            !isNotificationPermissionGranted(context) -> {
                handleNotificationsPermissionRequest(
                    activity,
                    onShouldShowDialog = onShowNotificationsSheet
                )
            }
            !isScheduleExactAlarm(context) -> onShowAlarmsSheet
            else -> {
                repository.setTitlesAlarmUpdate(value)
                repository.planTitlesUpdate(context)
            }
        }
    }
    fun setTitlesAlarmMins (context: Context, value: Int) = viewModelScope.launch {
        repository.setTitlesAlarmMins(value)
        repository.planTitlesUpdate(context)
    }
    fun setTitlesUpdFrequency (context: Context, value: Int) = viewModelScope.launch {
        repository.setTitlesAutoUpdateFrequency(value)
        repository.planTitlesUpdate(context)
    }
    fun setAlarmsAllowed(value: Boolean) = viewModelScope.launch { repository.setExactAlarmsAllowed(value) }
    fun planTitlesAutoUpdate(context: Context) = viewModelScope.launch { repository.planTitlesUpdate(context) }
    fun setProxyEnabled(value: Boolean) = viewModelScope.launch { repository.setProxyEnabled(value) }

    fun isApiKeyDefault(apiKey: String): Boolean {
        return apiKey == _defaultApiKey
    }

    fun setShowAlarmsSheet(value: Boolean) {
        _showAlarmsSheet.value = value
    }

    fun setShowNotificationsSheet(value: Boolean) {
        _showNotificationSheet.value = value
    }

    fun scrollToTop() {
        _scrollEvents.trySend(SettingsScrollEvent.ScrollToTop)
    }
}

sealed interface SettingsScrollEvent {
    data object ScrollToTop : SettingsScrollEvent
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