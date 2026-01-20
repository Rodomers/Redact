package com.rds.mews.repositories

import android.annotation.SuppressLint
import android.content.Context
import com.rds.mews.GeminiApiKeyProvider
import com.rds.mews.ProxyAddressProvider
import com.rds.mews.R
import com.rds.mews.RSSHubAddressProvider
import com.rds.mews.RssHubApiKeyProvider
import com.rds.mews.core.DbHelper
import com.rds.mews.core.getRssName
import com.rds.mews.core.validateGeminiKey
import com.rds.mews.localcore.*
import com.rds.mews.settings_manager.AppSettings
import com.rds.mews.settings_manager.SettingsManager
import com.rds.mews.ui.custom_elements.TabScreen
import com.rds.mews.workers.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

object MewsRepository {
    private lateinit var db: DbHelper
    @SuppressLint("StaticFieldLeak")
    private lateinit var settingsManager: SettingsManager
    private lateinit var externalScope: CoroutineScope

    // --- StateFlows (Status & Technical) ---
    lateinit var lastError: StateFlow<SummarizationResult.Failure?>
    lateinit var lastTitlesUpdate: StateFlow<Long>
    lateinit var updatingTitles: StateFlow<Boolean>
    lateinit var updatingState: StateFlow<String?>
    lateinit var updatingProgress: StateFlow<Float>
    lateinit var bannedNewsFlow: StateFlow<Set<String>>

    // --- StateFlows (User Settings / Enums) ---
    lateinit var darkTheme: StateFlow<DarkTheme>           // Было String currentTheme
    lateinit var appTheme: StateFlow<AppTheme>             // Было Boolean monetColors
    lateinit var titlesNum: StateFlow<HeadersNum>          // Было Int
    lateinit var titlesPeriod: StateFlow<TitlesPeriod>     // Было Int
    lateinit var llmModel: StateFlow<GeminiModelOption>    // Было String currentLlmModel
    lateinit var titlesAutoUpdateFrequency: StateFlow<AutoUpdateFrequency> // Было Int

    // --- StateFlows (User Settings / Primitives) ---
    lateinit var userApiKey: StateFlow<String>
    lateinit var showDates: StateFlow<Boolean>
    lateinit var rssUpdateInterval: StateFlow<Int>
    lateinit var lastRssUpdate: StateFlow<Long>
    lateinit var proxyEnabled: StateFlow<Boolean>
    lateinit var compactTabBar: StateFlow<Boolean>
    lateinit var filterTopics: StateFlow<Boolean>
    lateinit var innerTimestamps: StateFlow<Boolean>
    lateinit var showSnippets: StateFlow<Boolean>
    lateinit var titlesAlarmUpdate: StateFlow<Boolean>
    lateinit var titlesAlarmTimeMins: StateFlow<Int>
    lateinit var exactAlarmsAllowed: StateFlow<Boolean>
    lateinit var notificationsGranted: StateFlow<Boolean>
    lateinit var currentLanguage: StateFlow<String?>

    // Runtime state (Not in Settings)
    private val _sourcesUpdateTrigger = MutableStateFlow(0)
    private val _context = MutableStateFlow<Context?>(null)
    private val _selectedTab = MutableStateFlow<TabScreen>(TabScreen.Sources)
    var selectedTab: StateFlow<TabScreen> = _selectedTab.asStateFlow()

    var isInitialized = false
        private set
    var DEFAULT_GEMINI_API_KEY: String = ""
    var SERVER_KEY: String = ""
    var PROXY_ADDRESS: String = ""
    var HUB_ADDRESS: String = ""

    // Legacy Keys (kept for compatibility with old code refs, if any)
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

    fun initialize(context: Context, externalScope: CoroutineScope) {
        if (isInitialized) return
        val appContext = context.applicationContext
        this.db = DbHelper(appContext)
        this.settingsManager = SettingsManager(appContext)
        this.externalScope = externalScope

        this.DEFAULT_GEMINI_API_KEY = GeminiApiKeyProvider().getKey()
        this.SERVER_KEY = RssHubApiKeyProvider().getKey()
        this.PROXY_ADDRESS = ProxyAddressProvider().getKey()
        this.HUB_ADDRESS = RSSHubAddressProvider().getKey()

        setContext(appContext)

        // Подключаемся к Flow настроек
        val settingsFlow = settingsManager.settings

        // Хелпер для создания StateFlow
        fun <T> createSettingFlow(mapBlock: (AppSettings) -> T, default: T): StateFlow<T> {
            return settingsFlow.map(mapBlock)
                .stateIn(externalScope, SharingStarted.Eagerly, default)
        }

        // --- MAPPING (AppSettings -> Repo StateFlows) ---

        // Status
        updatingTitles = createSettingFlow({ it.updatingTitles }, false)
        updatingState = createSettingFlow({ it.updatingState }, "off")
        updatingProgress = createSettingFlow({ it.updatingProgress }, 0f)
        lastTitlesUpdate = createSettingFlow({ it.lastTitlesUpdate }, 0L)
        bannedNewsFlow = createSettingFlow({ it.bannedNews }, emptySet())

        // Enums mapping
        darkTheme = createSettingFlow({ it.darkTheme }, DarkTheme.SYSTEM)
        appTheme = createSettingFlow({ it.appTheme }, AppTheme.DEFAULT)
        titlesNum = createSettingFlow({ it.titlesNum }, HeadersNum.NUM_10)
        titlesPeriod = createSettingFlow({ it.titlesPeriod }, TitlesPeriod.HRS_24)
        titlesAutoUpdateFrequency = createSettingFlow({ it.titlesAutoUpdateFrequency }, AutoUpdateFrequency.FREQ_24)
        llmModel = createSettingFlow({ it.llmModel }, GeminiModelOption.FLASH_LATEST)

        // API Key Logic
        userApiKey = createSettingFlow({
            it.userApiKey.ifBlank { DEFAULT_GEMINI_API_KEY }
        }, DEFAULT_GEMINI_API_KEY)

        // Primitives
        showDates = createSettingFlow({ it.showDates }, false)
        rssUpdateInterval = createSettingFlow({ it.rssUpdateInterval }, 30)
        lastRssUpdate = createSettingFlow({ it.lastRssUpdate }, 0L)
        proxyEnabled = createSettingFlow({ it.enableProxy }, false)
        compactTabBar = createSettingFlow({ it.compactTabBar }, false)
        filterTopics = createSettingFlow({ it.filterTopics }, false)
        innerTimestamps = createSettingFlow({ it.innerTimestamps }, false)
        showSnippets = createSettingFlow({ it.showSnippets }, false)
        titlesAlarmUpdate = createSettingFlow({ it.titlesAutoUpdate }, false)
        titlesAlarmTimeMins = createSettingFlow({ it.titlesAlarmTimeMins }, 540)
        exactAlarmsAllowed = createSettingFlow({ it.alarmsAllowed }, false)
        notificationsGranted = createSettingFlow({ it.notificationsGranted }, false)

        val defaultLang = getStringResource(R.string.current_language)
        currentLanguage = createSettingFlow({ it.currentLanguage ?: defaultLang }, defaultLang)

        // Error Mapping
        lastError = settingsFlow.map { settings ->
            settings.lastError?.let { saved ->
                SummarizationResult.Failure(
                    type = saved.type,
                    cause = Exception(saved.message)
                )
            }
        }.stateIn(externalScope, SharingStarted.Eagerly, null)

        isInitialized = true
    }

    val darkThemeList: List<DarkTheme> get() = DarkTheme.entries
    val appThemeList: List<AppTheme> get() = AppTheme.entries
    val headersNumList: List<HeadersNum> get() = HeadersNum.entries
    val titlesPeriodList: List<TitlesPeriod> get() = TitlesPeriod.entries
    val autoUpdateFrequencyList: List<AutoUpdateFrequency> get() = AutoUpdateFrequency.entries

    val geminiModelsList: List<GeminiModelOption> get() = GeminiModelOption.entries
    val defaultModel: GeminiModelOption get() = GeminiModelOption.FLASH_LITE_LATEST

    suspend fun checkGeminiApiKey(key: String): Boolean {
        return validateGeminiKey(
            key,
            PROXY_ADDRESS,
            SERVER_KEY,
            proxyEnabled.value
        )
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

    private val _titlesUpdateTrigger = MutableStateFlow(0)
    @OptIn(ExperimentalCoroutinesApi::class)
    val titles: Flow<List<Title>> = _titlesUpdateTrigger.flatMapLatest {
        flow { emit(db.getTitles()) }
    }.flowOn(Dispatchers.IO)

    fun triggerTitlesRefresh() {
        _titlesUpdateTrigger.value++
    }

    fun startTitlesUpdate(context: Context) {
        setTitlesUpdate(context)
    }

    fun delTitles(time: Long? = null) {
        db.titlesTimeKill(time ?: 0)
    }

    fun getMessages(ids: String): List<Message>? {
        val arr =  db.dbPack(ids).split(", ")
            .mapNotNull { db.getMessage(it.toLongOrNull()) }
        return arr.ifEmpty { null }
    }

    fun getTitlesCount(): Int = db.getTitles().size

    suspend fun getRssName(link: String): String? {
        return getRssName(link, proxyEnabled.value)
    }

    // --- Setters (Now accepting Enums) ---

    fun setCurrentTab(newTab: TabScreen) { _selectedTab.value = newTab }

    // Theme (DarkTheme Enum)
    fun setDarkTheme(newValue: DarkTheme) = updateSetting { it.copy(darkTheme = newValue) }

    // App Theme (AppTheme Enum)
    fun setAppTheme(newValue: AppTheme) = updateSetting { it.copy(appTheme = newValue) }

    // Headers Num (HeadersNum Enum)
    fun setTitlesNum(newValue: HeadersNum) = updateSetting { it.copy(titlesNum = newValue) }

    // Titles Period (TitlesPeriod Enum)
    fun setTitlesPeriod(newValue: TitlesPeriod) = updateSetting { it.copy(titlesPeriod = newValue) }

    // LLM Model (GeminiModelOption Enum)
    fun setLlmModel(newValue: GeminiModelOption) = updateSetting { it.copy(llmModel = newValue) }

    // Auto Update Frequency (AutoUpdateFrequency Enum)
    fun setTitlesAutoUpdateFrequency(newValue: AutoUpdateFrequency) = updateSetting { it.copy(titlesAutoUpdateFrequency = newValue) }

    fun setUserApiKey(newValue: String) = updateSetting { it.copy(userApiKey = newValue) }

    fun setShowDates(newValue: Boolean) = updateSetting { it.copy(showDates = newValue) }

    fun setRssUpdateInterval(context: Context, newValue: Int) {
        externalScope.launch {
            settingsManager.updateSettings { it.copy(rssUpdateInterval = newValue) }
            AlarmScheduler.cancel(context, true)
            setRssUpdate(context, intervalMin = newValue)
        }
    }

    fun setLastRssUpdate(newValue: Long) = updateSetting { it.copy(lastRssUpdate = newValue) }

    fun setProxyEnabled(newValue: Boolean) = updateSetting { it.copy(enableProxy = newValue) }

    fun setLastTitlesUpdate(newValue: Long) = updateSetting { it.copy(lastTitlesUpdate = newValue) }

    fun setUpdatingTitles(newValue: Boolean) = updateSetting { it.copy(updatingTitles = newValue) }

    fun setUpdatingState(newValue: String) = updateSetting { it.copy(updatingState = newValue) }

    fun setUpdatingProgress(newValue: Float) = updateSetting { it.copy(updatingProgress = newValue) }

    fun setBannedNews(newValue: Set<String>) = updateSetting { it.copy(bannedNews = newValue) }

    fun addBannedNew(newValue: String) {
        if (!bannedNewsFlow.value.contains(newValue)) {
            updateSetting { it.copy(bannedNews = it.bannedNews + newValue) }
        }
    }

    fun delBannedNew(value: String) {
        if (bannedNewsFlow.value.contains(value)) {
            updateSetting { it.copy(bannedNews = it.bannedNews - value) }
        }
    }

    fun setCompactTab(newValue: Boolean) = updateSetting { it.copy(compactTabBar = newValue) }

    fun setFilterTopics(newValue: Boolean) = updateSetting { it.copy(filterTopics = newValue) }

    fun setInnerTimestamps(newValue: Boolean) = updateSetting { it.copy(innerTimestamps = newValue) }

    fun setShowSnippets(newValue: Boolean) = updateSetting { it.copy(showSnippets = newValue) }

    fun setTitlesAlarmUpdate(newValue: Boolean) = updateSetting { it.copy(titlesAutoUpdate = newValue) }

    fun setTitlesAlarmMins(newValue: Int) = updateSetting { it.copy(titlesAlarmTimeMins = newValue) }

    fun setExactAlarmsAllowed(newValue: Boolean) {
        externalScope.launch {
            settingsManager.updateSettings { it.copy(alarmsAllowed = newValue, titlesAutoUpdate = if(!newValue) false else it.titlesAutoUpdate) }
        }
    }

    fun setNotificationsGranted(newValue: Boolean) {
        externalScope.launch {
            settingsManager.updateSettings { it.copy(notificationsGranted = newValue, titlesAutoUpdate = if(!newValue) false else it.titlesAutoUpdate) }
        }
    }

    fun setCurrentLanguage(newValue: String) = updateSetting { it.copy(currentLanguage = newValue) }

    // --- Error Handling ---

    fun saveLastError(failure: SummarizationResult.Failure) {
        externalScope.launch { settingsManager.saveLastError(failure) }
    }

    fun clearError() {
        externalScope.launch { settingsManager.clearLastError() }
    }

    // --- Alarm Logic ---

    fun cancelTitlesAutoUpdates(context: Context) {
        AlarmScheduler.cancel(context)
    }

    fun planTitlesUpdate(context: Context) {
        // Берем значения напрямую из StateFlows (ENUMS)
        if (titlesAlarmUpdate.value) {
            // Извлекаем числовое значение из Enum (.num)
            val updateFrequencyHours = titlesAutoUpdateFrequency.value.num
            val updateTimeMins = titlesAlarmTimeMins.value

            val nextRunTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, updateTimeMins / 60)
                set(Calendar.MINUTE, updateTimeMins % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Логика планирования
            while (nextRunTime.before(Calendar.getInstance())) {
                nextRunTime.add(Calendar.HOUR_OF_DAY, updateFrequencyHours)
            }

            val nextRunTimeMillis = nextRunTime.timeInMillis
            AlarmScheduler.schedule(context, nextRunTimeMillis)

            println("MewsRepository: Следующее обновление запланировано на ${Date(nextRunTimeMillis)}")

        } else {
            AlarmScheduler.cancel(context)
            println("MewsRepository: Автообновление отключено, запланированные задачи отменены.")
        }
    }

    // --- Helpers ---

    private fun updateSetting(transform: (AppSettings) -> AppSettings) {
        externalScope.launch {
            settingsManager.updateSettings(transform)
        }
    }

    private fun setContext(context: Context) {
        _context.value = context
    }

    fun getStringResource(id: Int): String? {
        return _context.value?.getString(id)
    }
}