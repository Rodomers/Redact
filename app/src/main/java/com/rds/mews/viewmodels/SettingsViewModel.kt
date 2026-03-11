package com.rds.mews.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rds.mews.MainActivity
import com.rds.mews.localcore.* // Импортируем все наши Enums
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val repository: MewsRepository) : ViewModel() {
    private val _scrollEvents = Channel<SettingsScrollEvent>(Channel.CONFLATED)
    val scrollEvents = _scrollEvents.receiveAsFlow()

    private val _showAlarmsSheet = MutableStateFlow(false)
    val showAlarmsSheet: StateFlow<Boolean> = _showAlarmsSheet

    private val _showNotificationSheet = MutableStateFlow(false)
    val showNotificationSheet: StateFlow<Boolean> = _showNotificationSheet

    private val _groupStates = MutableStateFlow<List<SettingsGroupState>>(emptyList())
    val groupStates = _groupStates.asStateFlow()

    private val _autoUpdateScreenOpened = MutableStateFlow(false)
    val autoUpdateScreenOpened: StateFlow<Boolean> = _autoUpdateScreenOpened

    private val _bannedNewsScreenOpened = MutableStateFlow(false)
    val bannedNewsScreenOpened: StateFlow<Boolean> = _bannedNewsScreenOpened

    private val _geminiKeyScreenOpened = MutableStateFlow(false)
    val geminiScreenOpened = _geminiKeyScreenOpened

    private val _geminiKeyBuffer = MutableStateFlow("")
    val geminiKeyBuffer = _geminiKeyBuffer

    private val _isApiKeyCorrect = MutableStateFlow(false)
    val isApiKeyCorrect = _isApiKeyCorrect

    val darkThemes: List<DarkTheme> = repository.darkThemeList
    val appThemes: List<AppTheme> = repository.appThemeList
    val headersNumList: List<HeadersNum> = repository.headersNumList
    val titlesPeriods: List<TitlesPeriod> = repository.titlesPeriodList
    val titlesKeepings: List<TitlesKeeping> = repository.titlesKeepingList
    val autoUpdateFrequencies: List<AutoUpdateFrequency> = repository.autoUpdateFrequencyList
    val geminiModels: List<GeminiModelOption> = repository.geminiModelsList
    val defaultGeminiModel: GeminiModelOption = repository.defaultModel

    val compactTabBar: StateFlow<Boolean> = repository.compactTabBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appTheme: StateFlow<AppTheme> = repository.appTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.DEFAULT)

    val darkTheme: StateFlow<DarkTheme> = repository.darkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DarkTheme.SYSTEM)

    val showDates: StateFlow<Boolean> = repository.showDates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val titlesNum: StateFlow<HeadersNum> = repository.titlesNum
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HeadersNum.NUM_10)

    val titlesPeriod: StateFlow<TitlesPeriod> = repository.titlesPeriod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TitlesPeriod.HRS_24)
    val titlesKeeping: StateFlow<TitlesKeeping> = repository.titlesKeeping
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TitlesKeeping.DAYS_1)

    val rssUpdateInterval: StateFlow<Int> = repository.rssUpdateInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)

    val filterTopics: StateFlow<Boolean> = repository.filterTopics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentLlm: StateFlow<GeminiModelOption> = repository.llmModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultGeminiModel)

    val userApi: StateFlow<String> = repository.userApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val innerTime: StateFlow<Boolean> = repository.innerTimestamps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showSnippets: StateFlow<Boolean> = repository.showSnippets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val titlesAlarmUpdate: StateFlow<Boolean> = repository.titlesAlarmUpdate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val titlesAlarmMins: StateFlow<Int> = repository.titlesAlarmTimeMins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 540)

    val titlesUpdateFrequency: StateFlow<AutoUpdateFrequency> = repository.titlesAutoUpdateFrequency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutoUpdateFrequency.FREQ_24)

    val exactAlarmsAllowed: StateFlow<Boolean> = repository.exactAlarmsAllowed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val proxyEnabled: StateFlow<Boolean> = repository.proxyEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _defaultApiKey = repository.DEFAULT_GEMINI_API_KEY

    val isKeyDefault: StateFlow<Boolean> = userApi.map { apiKey ->
        apiKey == _defaultApiKey
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = isApiKeyDefault(repository.userApiKey.value)
    )

    val bannedNews = repository.bannedNewsFlow

    fun setGeminiKeyBuffer(value: String) {
        val default = value == _defaultApiKey

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _geminiKeyBuffer.value = value
                if (!default) _isApiKeyCorrect.value = repository.checkGeminiApiKey(value)
            }
        }
    }

    fun addGroupState(group: Int, initState: Boolean = true) {
        if (_groupStates.value.indexOfFirst { it.group == group } == -1)
            _groupStates.value += SettingsGroupState(group, initState)
    }

    fun changeGroupState(group: Int) {
        val current = _groupStates.value.map {
            if (it.group == group) it.copy(expanded = !it.expanded)
            else it
        }
        _groupStates.value = current
    }

    fun setAutoupdateScreen(value: Boolean) {
        _autoUpdateScreenOpened.value = value
    }

    fun setBannedNewsScreen(value: Boolean) {
        _bannedNewsScreenOpened.value = value
    }

    fun setGeminiScreen(value: Boolean) {
        if (_geminiKeyBuffer.value == "" && !isKeyDefault.value) {
            setGeminiKeyBuffer(repository.userApiKey.value)
        }
        _geminiKeyScreenOpened.value = value
    }

    fun setCompactTab(value: Boolean) = viewModelScope.launch { repository.setCompactTab(value) }

    fun setAppTheme(value: AppTheme) = viewModelScope.launch { repository.setAppTheme(value) }

    fun setDarkTheme(value: DarkTheme) = viewModelScope.launch { repository.setDarkTheme(value) }

    fun setShowDates(value: Boolean) = viewModelScope.launch { repository.setShowDates(value) }

    fun setTitlesNum(value: HeadersNum) = viewModelScope.launch { repository.setTitlesNum(value) }

    fun setTitlesPeriod(value: TitlesPeriod) = viewModelScope.launch { repository.setTitlesPeriod(value) }
    fun setTitlesKeeping(value: TitlesKeeping) = viewModelScope.launch { repository.setTitlesKeeping(value) }

    fun setRssUpdateInterval(context: Context, value: Int) {
        viewModelScope.launch {
            repository.setRssUpdateInterval(context, value)
        }
    }

    fun setFilterTopics(value: Boolean) = viewModelScope.launch { repository.setFilterTopics(value) }

    fun setCurrentLlm(value: GeminiModelOption) = viewModelScope.launch { repository.setLlmModel(value) }

    fun setUserGeminiApi(value: String) = viewModelScope.launch {
        repository.setUserApiKey(value)
    }

    fun resetApiKey() = viewModelScope.launch {
        repository.setUserApiKey(_defaultApiKey)
        repository.setLlmModel(defaultGeminiModel)
        repository.setFilterTopics(false)
        if (repository.titlesNum.value.num > 20) repository.setTitlesNum(HeadersNum.NUM_10)
    }

    fun setInnerTime(value: Boolean) = viewModelScope.launch { repository.setInnerTimestamps(value) }
    fun setShowSnippets(value: Boolean) = viewModelScope.launch { repository.setShowSnippets(value) }
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
            !isScheduleExactAlarm(context) -> onShowAlarmsSheet()
            else -> {
                repository.setTitlesAlarmUpdate(value)
                repository.planTitlesUpdate(context, alarmUpdateValue = value)
            }
        }
    }

    fun setTitlesAlarmMins(context: Context, value: Int) = viewModelScope.launch {
        repository.setTitlesAlarmMins(value)
        repository.planTitlesUpdate(context, explicitTimeMins = value)
    }

    fun setTitlesUpdFrequency(context: Context, value: AutoUpdateFrequency) = viewModelScope.launch {
        repository.setTitlesAutoUpdateFrequency(value)
        repository.planTitlesUpdate(context)
    }

    fun setAlarmsAllowed(value: Boolean) = viewModelScope.launch { repository.setExactAlarmsAllowed(value) }
    fun planTitlesAutoUpdate(context: Context) = viewModelScope.launch { repository.planTitlesUpdate(context) }
    fun setProxyEnabled(value: Boolean) = viewModelScope.launch { repository.setProxyEnabled(value) }

    fun isApiKeyDefault(apiKey: String): Boolean {
        return apiKey == _defaultApiKey
    }

    fun clearFeed() {
        repository.delTitles()
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