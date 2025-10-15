package com.rds.mews

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MewsRepository {
    private lateinit var db: DbHelper
    private lateinit var settingsManager: SettingsManager
    private var isInitialized = false
    private val _sourcesUpdateTrigger = MutableStateFlow(0)

    fun initialize(context: Context) {
        if (isInitialized) return
        val context = context.applicationContext
        this.db = DbHelper(context)
        this.settingsManager = SettingsManager(context)

        loadInitSettings()
        listenForErrors()
        checkForSavedError()
        setContext(context)
        isInitialized = true
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val sources: Flow<List<RSS>> = _sourcesUpdateTrigger.flatMapLatest {
        flow { emit(db.getRSS()) }
    }.flowOn(Dispatchers.IO)

    suspend fun addSource(context: Context, name: String, link: String) {
        withContext(Dispatchers.IO) {
            addSource(name, link, db)
            _sourcesUpdateTrigger.value ++
            AlarmScheduler.cancel(context, true)
            setRssUpdate(context, true, rssUpdateInterval.value)
        }
    }

    suspend fun deleteSource(name: String) {
        withContext(Dispatchers.IO) {
            delSource(name, db)
            _sourcesUpdateTrigger.value ++
        }
    }

    suspend fun changeSource(oldName: String, newName: String) {
        withContext(Dispatchers.IO) {
            changeSource(oldName, newName, db)
            _sourcesUpdateTrigger.value ++
        }
    }


    private val _titlesUpdateTrigger = MutableStateFlow(0)
    @OptIn(ExperimentalCoroutinesApi::class)
    val titles: Flow<List<Title>> = _titlesUpdateTrigger.flatMapLatest {
        flow {
            emit(db.getTitles())
        }
    }.flowOn(Dispatchers.IO)

    fun triggerTitlesRefresh() {
        _titlesUpdateTrigger.value++
    }

    fun startTitlesUpdate(context: Context) {
        setTitlesUpdate(context)
    }

    const val CURRENT_THEME = "current_theme"
    const val IS_MONET = "is_monet"
    const val TITLES_NUM = "titles_num"
    const val TITLES_PERIOD = "titles_period"
    const val USER_API_KEY = "user_api"
    const val CURRENT_LLM_MODEL = "current_model"
    const val SHOW_DATES = "show_dates"
    const val RSS_UPDATE_INTERVAL = "rss_update_interval"
    const val LAST_RSS_UPDATE = "last_rss_update"
    const val LAST_TITLES_UPDATE = "last_titles_update"
    const val UPDATING_TITLES = "updating_titles"
    const val UPDATING_STATE = "updating_state"
    const val COMPACT_TAB_BAR = "compact_tab_bar"
    const val FILTER_TOPICS = "filter_topics"
    const val ENLARGE_TIMESTAMPS = "endure_time"
    const val TITLES_AUTO_UPDATE = "auto_update_titles"
    const val TITLES_AUTO_UPDATE_TIME = "auto_update_titles_time"
    const val ALARMS_ALLOWED = "exact_alarms_allowed"

    private val _selectedTab = MutableStateFlow<TabScreen>(TabScreen.Sources)
    var selectedTab: StateFlow<TabScreen> = _selectedTab.asStateFlow()
    fun setCurrentTab(newTab: TabScreen) {
        _selectedTab.value = newTab
    }

    private val _currentTheme = MutableStateFlow("system")
    val currentTheme: StateFlow<String> = _currentTheme.asStateFlow()
    fun setCurrentTheme(newValue: String) {
        settingsManager.saveString(CURRENT_THEME, newValue)
        _currentTheme.value = newValue
    }

    private val _monetColors = MutableStateFlow(false)
    val monetColors: StateFlow<Boolean> = _monetColors.asStateFlow()
    fun setMonetColors(newValue: Boolean) {
        settingsManager.saveBoolean(IS_MONET, newValue)
        _monetColors.value = newValue
    }

    private val _titlesNum = MutableStateFlow(10)
    val titlesNum: StateFlow<Int> = _titlesNum.asStateFlow()
    fun setTitlesNum(newValue: Int) {
        settingsManager.saveInt(TITLES_NUM, newValue)
        _titlesNum.value = newValue
    }

    private val _titlesPeriod = MutableStateFlow(24)
    val titlesPeriod: StateFlow<Int> = _titlesPeriod.asStateFlow()
    fun setTitlesPeriod(newValue: Int) {
        settingsManager.saveInt(TITLES_PERIOD, newValue)
        _titlesPeriod.value = newValue
    }

    const val DEFAULT_GEMINI_API_KEY: String = "AIzaSyCNNpbcjd8lMRMtD6naikNMaRxnG-0HHkk"
    private val _userApiKey = MutableStateFlow(DEFAULT_GEMINI_API_KEY)
    val userApiKey: StateFlow<String> = _userApiKey.asStateFlow()
    fun setUserApiKey(newValue: String) {
        settingsManager.saveString(USER_API_KEY, newValue)
        _userApiKey.value = newValue
    }

    private val _currentLlmModel = MutableStateFlow("gemini-2.0-flash")
    val currentLlmModel: StateFlow<String> = _currentLlmModel.asStateFlow()
    fun setCurrentLlmModel(newValue: String) {
        settingsManager.saveString(CURRENT_LLM_MODEL, newValue)
        _currentLlmModel.value = newValue
    }

    private val _showDates = MutableStateFlow(false)
    val showDates: StateFlow<Boolean> = _showDates.asStateFlow()
    fun setShowDates(newValue: Boolean) {
        settingsManager.saveBoolean(SHOW_DATES, newValue)
        _showDates.value = newValue
    }

    private val _rssUpdateInterval = MutableStateFlow(15)
    val rssUpdateInterval: StateFlow<Int> = _rssUpdateInterval.asStateFlow()
    fun setRssUpdateInterval(context: Context, newValue: Int) {
        settingsManager.saveInt(RSS_UPDATE_INTERVAL, newValue)
        _rssUpdateInterval.value = newValue
        AlarmScheduler.cancel(context, true)
        setRssUpdate(context, intervalMin = rssUpdateInterval.value)
    }

    private val _lastRssUpdate = MutableStateFlow(0L)
    val lastRssUpdate: StateFlow<Long> = _lastRssUpdate.asStateFlow()
    fun setLastRssUpdate(newValue: Long) {
        settingsManager.saveLong(LAST_RSS_UPDATE, newValue)
        _lastRssUpdate.value = newValue
    }

    private val _lastTitlesUpdate = MutableStateFlow(0L)
    val lastTitlesUpdate: StateFlow<Long> = _lastTitlesUpdate.asStateFlow()
    fun setLastTitlesUpdate(newValue: Long) {
        settingsManager.saveLong(LAST_TITLES_UPDATE, newValue)
        _lastTitlesUpdate.value = newValue
    }

    private val _updatingTitles = MutableStateFlow(false)
    val updatingTitles: StateFlow<Boolean> = _updatingTitles.asStateFlow()
    fun setUpdatingTitles(newValue: Boolean) {
        settingsManager.saveBoolean(UPDATING_TITLES, newValue)
        _updatingTitles.value = newValue
    }

    private val _updatingState = MutableStateFlow("off")
    val updatingState: StateFlow<String> = _updatingState.asStateFlow()
    fun setUpdatingState(newValue: String) {
        settingsManager.saveString(UPDATING_STATE, newValue)
        _updatingState.value = newValue
    }

    private val _compactTabBar = MutableStateFlow(false)
    val compactTabBar: StateFlow<Boolean> = _compactTabBar.asStateFlow()
    fun setCompactTab(newValue: Boolean) {
        settingsManager.saveBoolean(COMPACT_TAB_BAR, newValue)
        _compactTabBar.value = newValue
    }

    private val _filterTopics = MutableStateFlow(false)
    val filterTopics: StateFlow<Boolean> = _filterTopics.asStateFlow()
    fun setFilterTopics(newValue: Boolean) {
        settingsManager.saveBoolean(FILTER_TOPICS, newValue)
        _filterTopics.value = newValue
    }

    private val _enlargedTime = MutableStateFlow(false)
    val enlargedTimestamps: StateFlow<Boolean> = _enlargedTime.asStateFlow()
    fun setEnlargeTimestamps(newValue: Boolean) {
        settingsManager.saveBoolean(ENLARGE_TIMESTAMPS, newValue)
        _enlargedTime.value = newValue
    }

    private val _titlesAutoUpdate = MutableStateFlow(false)
    val titlesAutoUpdate: StateFlow<Boolean> = _titlesAutoUpdate.asStateFlow()
    fun setTitlesAutoUpdate(newValue: Boolean) {
        settingsManager.saveBoolean(TITLES_AUTO_UPDATE, newValue)
        _titlesAutoUpdate.value = newValue
    }
    fun cancelTitlesAutoUpdates(context: Context) {
        AlarmScheduler.cancel(context)
    }

    private val _exactAlarmsAllowed = MutableStateFlow(false)
    val exactAlarmsAllowed: StateFlow<Boolean> = _exactAlarmsAllowed.asStateFlow()
    fun setExactAlarmsAllowed(newValue: Boolean) {
        settingsManager.saveBoolean(ALARMS_ALLOWED, newValue)
        _exactAlarmsAllowed.value = newValue
    }

    private val _lastError = MutableStateFlow<SummarizationResult.Failure?>(null)
    val lastError: StateFlow<SummarizationResult.Failure?> = _lastError.asStateFlow()

    fun saveLastError(failure: SummarizationResult.Failure) {
        settingsManager.saveLastError(failure)
    }

    private val _context = MutableStateFlow<Context?>(null)
    fun setContext(context: Context) {
        _context.value = context
    }

    private fun listenForErrors() {
        CoroutineScope(Dispatchers.Default).launch {
            settingsManager.lastErrorFlow.collect { error ->
                _lastError.value = error
            }
        }
    }

    private fun checkForSavedError() {
        _lastError.value = settingsManager.getLastError()
    }

    fun clearError() {
        _lastError.value = null
        settingsManager.clearLastError()
    }

    fun getStringResource(id: Int): String? {
        return when (_context.value) {
            null -> null
            else -> _context.value!!.getString(id)
        }
    }

    private fun loadInitSettings() {
        _currentTheme.value = settingsManager.getString(CURRENT_THEME, "system")
        _monetColors.value = settingsManager.getBoolean(IS_MONET, false)
        _compactTabBar.value = settingsManager.getBoolean(COMPACT_TAB_BAR, false)
        _showDates.value = settingsManager.getBoolean(SHOW_DATES, false)
        _enlargedTime.value = settingsManager.getBoolean(ENLARGE_TIMESTAMPS, false)
        _titlesAutoUpdate.value = settingsManager.getBoolean(TITLES_AUTO_UPDATE, false)
        _exactAlarmsAllowed.value = settingsManager.getBoolean(ALARMS_ALLOWED, false)

        _titlesNum.value = settingsManager.getInt(TITLES_NUM, 10)
        _titlesPeriod.value = settingsManager.getInt(TITLES_PERIOD, 24)
        _userApiKey.value = settingsManager.getString(USER_API_KEY, DEFAULT_GEMINI_API_KEY)
        _currentLlmModel.value = settingsManager.getString(CURRENT_LLM_MODEL, "gemini-2.0-flash")
        _filterTopics.value = settingsManager.getBoolean(FILTER_TOPICS, false)

        _rssUpdateInterval.value = settingsManager.getInt(RSS_UPDATE_INTERVAL, 15)
        _lastRssUpdate.value = settingsManager.getLong(LAST_RSS_UPDATE, 0L)
        _lastTitlesUpdate.value = settingsManager.getLong(LAST_TITLES_UPDATE, 0L)

        _updatingTitles.value = settingsManager.getBoolean(UPDATING_TITLES, false)
        _updatingState.value = settingsManager.getString(UPDATING_STATE, "off")
    }
}