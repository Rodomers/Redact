package com.rds.mews.repositories

import android.content.Context
import com.rds.mews.workers.AlarmScheduler
import com.rds.mews.core.DbHelper
import com.rds.mews.GeminiApiKeyProvider
import com.rds.mews.localcore.Message
import com.rds.mews.R
import com.rds.mews.localcore.RSS
import com.rds.mews.RssHubApiKeyProvider
import com.rds.mews.ServerAddressProvider
import com.rds.mews.core.getRssName
import com.rds.mews.localcore.SettingsManager
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.localcore.Title
import com.rds.mews.localcore.linkTransform
import com.rds.mews.localcore.setRssUpdate
import com.rds.mews.localcore.setTitlesUpdate
import com.rds.mews.ui.custom_elements.TabScreen
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
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.Date

object MewsRepository {
    private lateinit var db: DbHelper
    private lateinit var settingsManager: SettingsManager
    private lateinit var externalScope: CoroutineScope
    lateinit var lastError: StateFlow<SummarizationResult.Failure?>
    lateinit var lastTitlesUpdate: StateFlow<Long>
    lateinit var updatingTitles: StateFlow<Boolean>
    lateinit var updatingState: StateFlow<String?>
    lateinit var updatingProgress: StateFlow<Float>
    lateinit var bannedNewsFlow: StateFlow<Set<String>>
    private var isInitialized = false
    private val _sourcesUpdateTrigger = MutableStateFlow(0)
    var DEFAULT_GEMINI_API_KEY: String = ""
    var RSS_HUB_KEY: String = ""
    var SERVER_IP: String = ""

    fun initialize(context: Context, externalScope: CoroutineScope) {
        if (isInitialized) return
        val context = context.applicationContext
        this.db = DbHelper(context)
        this.settingsManager = SettingsManager(context)
        this.externalScope = externalScope
        this.DEFAULT_GEMINI_API_KEY = GeminiApiKeyProvider().getKey()
        this.RSS_HUB_KEY = RssHubApiKeyProvider().getKey()
        this.SERVER_IP = ServerAddressProvider().getKey()

        loadInitSettings()
        setContext(context)
        isInitialized = true

        lastError = settingsManager.lastErrorFlow
            .stateIn(
                scope = MewsRepository.externalScope,
                started = SharingStarted.Companion.WhileSubscribed(5000),
                initialValue = null
            )

        lastTitlesUpdate = settingsManager.lastTitlesUpdateFlow
            .stateIn(
                scope = MewsRepository.externalScope,
                started = SharingStarted.Companion.WhileSubscribed(5000),
                initialValue = settingsManager.getLong(LAST_TITLES_UPDATE, 0L)
            )

        updatingTitles = settingsManager.updatingTitlesFlow
            .stateIn(
                scope = MewsRepository.externalScope,
                started = SharingStarted.Companion.WhileSubscribed(5000),
                initialValue = settingsManager.getBoolean(UPDATING_TITLES, false)
            )

        updatingState = settingsManager.updatingTitlesStateFlow
            .stateIn(
                scope = MewsRepository.externalScope,
                started = SharingStarted.Companion.WhileSubscribed(5000),
                initialValue = settingsManager.getString(UPDATING_STATE, "off")
            )

        updatingProgress = settingsManager.updatingTitlesProgressStateFlow
            .stateIn(
                scope = MewsRepository.externalScope,
                started = SharingStarted.Companion.WhileSubscribed(5000),
                initialValue = settingsManager.getFloat(UPDATING_PROGRESS, 0f)
            )

        bannedNewsFlow = settingsManager.bannedNewsFlow
            .stateIn(
                scope = MewsRepository.externalScope,
                started = SharingStarted.Companion.WhileSubscribed(5000),
                initialValue = settingsManager.getStringSet(BANNED_NEWS_SET, setOf(""))
        )
    }

    fun containsSetting(key: String): Boolean {
        return settingsManager.checkValue(key)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val sources: Flow<List<RSS>> = _sourcesUpdateTrigger.flatMapLatest {
        flow { emit(db.getRSS()) }
    }.flowOn(Dispatchers.IO)

    fun addSource(context: Context, name: String, link: String) {
        db.addRSS(name, linkTransform(link))
        _sourcesUpdateTrigger.value++
        AlarmScheduler.cancel(context, true)
        setLastRssUpdate(0L)
        setRssUpdate(context, true, rssUpdateInterval.value)
    }

    fun deleteSource(id: Long) {
        db.delRSS(id = id)
        _sourcesUpdateTrigger.value++
    }

    fun changeSource(id: Long, newName: String) {
        db.changeRssSource(id = id, newSource = newName)
        _sourcesUpdateTrigger.value++
        _titlesUpdateTrigger.value++
    }

    fun planTitlesUpdate(context: Context) {
        if (titlesAlarmUpdate.value) {
            val updateFrequencyHours = titlesAutoUpdateFrequency.value
            val updateTimeMins = titlesAlarmTimeMins.value

            val nextRunTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, updateTimeMins / 60)
                set(Calendar.MINUTE, updateTimeMins % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            while (nextRunTime.before(Calendar.getInstance())) {
                if (updateFrequencyHours == 12) nextRunTime.add(Calendar.HOUR_OF_DAY, 12)
                else nextRunTime.add(Calendar.HOUR_OF_DAY, 24)
            }

            val nextRunTimeMillis = nextRunTime.timeInMillis
            AlarmScheduler.schedule(context, nextRunTimeMillis)

            println("MewsRepository: Следующее обновление запланировано на ${Date(nextRunTimeMillis)}")

        } else {
            AlarmScheduler.cancel(context)
            println("MewsRepository: Автообновление отключено, запланированные задачи отменены.")
        }
    }

    fun delTitles(time: Long? = null) {
        db.titlesTimeKill(time?:0)
    }

    fun getMessages(ids: String): List<Message>? {
        val arr =  db.dbPack(ids).split(", ")
            .mapNotNull {
                db.getMessage(it.toLongOrNull())
            }
        return arr.ifEmpty { null }
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
    const val UPDATING_PROGRESS = "updating_progress"
    const val COMPACT_TAB_BAR = "compact_tab_bar"
    const val FILTER_TOPICS = "filter_topics"
    const val INNER_TIMESTAMPS = "inner_timestamps"
    const val SHOW_SNIPPETS = "show_snippets"
    const val TITLES_AUTO_UPDATE = "auto_update_titles"
    const val TITLES_AUTO_UPDATE_FREQUENCY = "auto_update_titles_frequency"
    const val ALARMS_ALLOWED = "exact_alarms_allowed"
    const val TITLES_ALARM_MINS = "titles_alarm_time"
    const val NOTIFICATIONS_GRANTED = "notifications_granted"
    const val CURRENT_LANGUAGE = "current_language"
    const val BANNED_NEWS_SET = "banned_news_set"
    const val ENABLE_PROXY = "enable_proxy"

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

    private val _enableProxy = MutableStateFlow(false)
    val proxyEnabled: StateFlow<Boolean> = _enableProxy.asStateFlow()
    fun setProxyEnabled(newValue: Boolean) {
        settingsManager.saveBoolean(ENABLE_PROXY, newValue)
        _enableProxy.value = newValue
    }

    fun setLastTitlesUpdate(newValue: Long) {
        settingsManager.saveLong(LAST_TITLES_UPDATE, newValue)
    }

    fun setUpdatingTitles(newValue: Boolean) {
        settingsManager.saveBoolean(UPDATING_TITLES, newValue)
    }

    fun setUpdatingState(newValue: String) {
        settingsManager.saveString(UPDATING_STATE, newValue)
    }

    fun setUpdatingProgress(newValue: Float) {
        settingsManager.saveFloat(UPDATING_PROGRESS, newValue)
    }

    fun setBannedNews(newValue: Set<String>) {
        settingsManager.saveStringSet(BANNED_NEWS_SET, newValue)
    }

    fun addBannedNew(newValue: String) {
        if (!bannedNewsFlow.value.contains(newValue)) settingsManager.saveStringSet(BANNED_NEWS_SET, bannedNewsFlow.value + newValue)
    }

    fun delBannedNew(value: String) {
        if (bannedNewsFlow.value.contains(value)) settingsManager.saveStringSet(BANNED_NEWS_SET, bannedNewsFlow.value - value)
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

    private val _innerTime = MutableStateFlow(false)
    val innerTimestamps: StateFlow<Boolean> = _innerTime.asStateFlow()
    fun setInnerTimestamps(newValue: Boolean) {
        settingsManager.saveBoolean(INNER_TIMESTAMPS, newValue)
        _innerTime.value = newValue
    }

    private val _showSnippets = MutableStateFlow(false)
    val showSnippets: StateFlow<Boolean> = _showSnippets.asStateFlow()
    fun setShowSnippets(newValue: Boolean) {
        settingsManager.saveBoolean(SHOW_SNIPPETS, newValue)
        _showSnippets.value = newValue
    }

    private val _titlesAlarmUpdate = MutableStateFlow(false)
    val titlesAlarmUpdate: StateFlow<Boolean> = _titlesAlarmUpdate.asStateFlow()
    fun setTitlesAlarmUpdate(newValue: Boolean) {
        settingsManager.saveBoolean(TITLES_AUTO_UPDATE, newValue)
        _titlesAlarmUpdate.value = newValue
    }
    fun cancelTitlesAutoUpdates(context: Context) {
        AlarmScheduler.cancel(context)
    }

    private val _titlesAlarmTimeMins = MutableStateFlow(540)
    val titlesAlarmTimeMins: StateFlow<Int> = _titlesAlarmTimeMins.asStateFlow()
    fun setTitlesAlarmMins(newValue: Int) {
        settingsManager.saveInt(TITLES_ALARM_MINS, newValue)
        _titlesAlarmTimeMins.value = newValue
    }

    private val _titlesAutoUpdateFrequency = MutableStateFlow(0)
    val titlesAutoUpdateFrequency: StateFlow<Int> = _titlesAutoUpdateFrequency.asStateFlow()
    fun setTitlesAutoUpdateFrequency(newValue: Int) {
        settingsManager.saveInt(TITLES_AUTO_UPDATE_FREQUENCY, newValue)
        _titlesAutoUpdateFrequency.value = newValue
    }

    private val _exactAlarmsAllowed = MutableStateFlow(false)
    val exactAlarmsAllowed: StateFlow<Boolean> = _exactAlarmsAllowed.asStateFlow()
    fun setExactAlarmsAllowed(newValue: Boolean) {
        settingsManager.saveBoolean(ALARMS_ALLOWED, newValue)
        _exactAlarmsAllowed.value = newValue
        if (!newValue) setTitlesAlarmUpdate(false)
    }

    private val _notificationsGranted = MutableStateFlow(false)
    val notificationsGranted: StateFlow<Boolean> = _notificationsGranted.asStateFlow()
    fun setNotificationsGranted(newValue: Boolean) {
        settingsManager.saveBoolean(NOTIFICATIONS_GRANTED, newValue)
        _notificationsGranted.value = newValue
        if (!newValue) setTitlesAlarmUpdate(false)
    }

    fun saveLastError(failure: SummarizationResult.Failure) {
        settingsManager.saveLastError(failure)
    }

    private val _context = MutableStateFlow<Context?>(null)
    fun setContext(context: Context) {
        _context.value = context
    }

    fun clearError() {
        settingsManager.clearLastError()
    }

    fun getStringResource(id: Int): String? {
        return when (_context.value) {
            null -> null
            else -> _context.value!!.getString(id)
        }
    }

    fun getTitlesCount(): Int {
        return db.getTitles().size
    }

    suspend fun getRssName(link: String): String? {
        return getRssName(link, _enableProxy.value)
    }

    private val _currentLanguage =
        MutableStateFlow<String?>(getStringResource(R.string.current_language))
    val currentLanguage = _currentLanguage.asStateFlow()
    fun setCurrentLanguage(newValue: String) {
        settingsManager.saveString(CURRENT_LANGUAGE, newValue)
        _currentLanguage.value = newValue
    }

    private fun loadInitSettings() {
        _currentTheme.value = settingsManager.getString(CURRENT_THEME, "system")
        _monetColors.value = settingsManager.getBoolean(IS_MONET, false)
        _compactTabBar.value = settingsManager.getBoolean(COMPACT_TAB_BAR, false)
        _showDates.value = settingsManager.getBoolean(SHOW_DATES, false)
        _innerTime.value = settingsManager.getBoolean(INNER_TIMESTAMPS, false)
        _showSnippets.value = settingsManager.getBoolean(SHOW_SNIPPETS, false)
        _titlesAlarmUpdate.value = settingsManager.getBoolean(TITLES_AUTO_UPDATE, false)
        _titlesAlarmTimeMins.value = settingsManager.getInt(TITLES_ALARM_MINS, 540)
        _titlesAutoUpdateFrequency.value = settingsManager.getInt(TITLES_AUTO_UPDATE_FREQUENCY, 24)
        _exactAlarmsAllowed.value = settingsManager.getBoolean(ALARMS_ALLOWED, false)
        _notificationsGranted.value = settingsManager.getBoolean(NOTIFICATIONS_GRANTED, false)
        _enableProxy.value = settingsManager.getBoolean(ENABLE_PROXY, false)

        _titlesNum.value = settingsManager.getInt(TITLES_NUM, 10)
        _titlesPeriod.value = settingsManager.getInt(TITLES_PERIOD, 24)
        _userApiKey.value = settingsManager.getString(USER_API_KEY, DEFAULT_GEMINI_API_KEY)
        _currentLlmModel.value = settingsManager.getString(CURRENT_LLM_MODEL, "gemini-2.0-flash")
        _filterTopics.value = settingsManager.getBoolean(FILTER_TOPICS, false)

        _rssUpdateInterval.value = settingsManager.getInt(RSS_UPDATE_INTERVAL, 15)
        _lastRssUpdate.value = settingsManager.getLong(LAST_RSS_UPDATE, 0L)
    }
}