package com.rds.mews.repositories

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rds.mews.GeminiApiKeyProvider
import com.rds.mews.MinifluxAddressProvider
import com.rds.mews.MinifluxApiKeyProvider
import com.rds.mews.ProxyAddressProvider
import com.rds.mews.R
import com.rds.mews.RSSHubAddressProvider
import com.rds.mews.RssHubApiKeyProvider
import com.rds.mews.core.NewsSummarizer
import com.rds.mews.core.SourceResolver
import com.rds.mews.database.AppDatabase
import com.rds.mews.core.validateGeminiKey
import com.rds.mews.database.MessageDao
import com.rds.mews.database.MessageEntity
import com.rds.mews.database.SourceDao
import com.rds.mews.database.SourceEntity
import com.rds.mews.database.TitleDao
import com.rds.mews.database.TitleEntity
import com.rds.mews.database.TitleMessageMap
import com.rds.mews.localcore.*
import com.rds.mews.settings_manager.AppSettings
import com.rds.mews.settings_manager.SettingsManager
import com.rds.mews.settings_manager.SummarizationErrorType
import com.rds.mews.text_filters.TextSanitizer
import com.rds.mews.ui.custom_elements.TabScreen
import com.rds.mews.workers.AlarmScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object MewsRepository {
    private lateinit var database: AppDatabase
    private lateinit var sourceDao: SourceDao
    private lateinit var messageDao: MessageDao
    private lateinit var titleDao: TitleDao

    @SuppressLint("StaticFieldLeak")
    private lateinit var settingsManager: SettingsManager
    private lateinit var externalScope: CoroutineScope
    private const val TAG = "MewsRepository"

    private val resolvedAvatars = MutableStateFlow<Map<Long, String>>(emptyMap())

    lateinit var lastError: StateFlow<SummarizationResult.Failure?>
    lateinit var lastTitlesUpdate: StateFlow<Long>
//    lateinit var updatingState: StateFlow<String?>
//    lateinit var updatingProgress: StateFlow<Float>
    lateinit var bannedNewsFlow: StateFlow<Set<String>>

    lateinit var darkTheme: StateFlow<DarkTheme>
    lateinit var appTheme: StateFlow<AppTheme>
    lateinit var expandSources: StateFlow<Boolean>
    lateinit var titleSorting: StateFlow<TitleSorting>
    lateinit var titlesNum: StateFlow<HeadersNum>
    lateinit var titlesPeriod: StateFlow<TitlesPeriod>
    lateinit var titlesKeeping: StateFlow<TitlesKeeping>
    lateinit var llmModel: StateFlow<GeminiModelOption>
    lateinit var titlesAutoUpdateFrequency: StateFlow<AutoUpdateFrequency>

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
    lateinit var parserBatchSize: StateFlow<Int>
    lateinit var sanitizeCopiedText: StateFlow<Boolean>
    lateinit var saveUnreadTitles: StateFlow<Boolean>
    lateinit var enableUpdateNotification: StateFlow<Boolean>

    private val _context = MutableStateFlow<Context?>(null)
    private val _selectedTab = MutableStateFlow<TabScreen>(TabScreen.Sources)
    var selectedTab: StateFlow<TabScreen> = _selectedTab.asStateFlow()

    var isInitialized = false
        private set
    var DEFAULT_GEMINI_API_KEY: String = ""
    var SERVER_KEY: String = ""
    var MINIFLUX_KEY: String = ""
    var PROXY_ADDRESS: String = ""
    var HUB_ADDRESS: String = ""
    var MINIFLUX_ADDRESS: String = ""

    private val _updatingTitles = MutableStateFlow(false)
    val updatingTitles = _updatingTitles.asStateFlow()

    private val _updatingState = MutableStateFlow(UpdatingState.DEFAULT)
    val updatingState = _updatingState.asStateFlow()

    private val _updatingProgress = MutableStateFlow(0f)
    val updatingProgress = _updatingProgress.asStateFlow()

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
    const val PARSER_BATCH_SIZE = "parser_batch_size"

    lateinit var sources: Flow<List<RSS>>
    lateinit var titles: Flow<List<Title>>

    fun initialize(context: Context, externalScope: CoroutineScope) {
        if (isInitialized) return
        val appContext = context.applicationContext

        this.database = Room.databaseBuilder(appContext, AppDatabase::class.java, "MainDB")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
//            .setQueryCallback({ sqlQuery, bindArgs ->
//                Log.d("ROOM_QUERY", "Выполнение: $sqlQuery | Параметры: $bindArgs")
//            }, java.util.concurrent.Executors.newSingleThreadExecutor())
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .build()
        this.sourceDao = database.sourceDao()
        this.messageDao = database.messageDao()
        this.titleDao = database.titleDao()

        this.settingsManager = SettingsManager(appContext)
        this.externalScope = externalScope

        this.DEFAULT_GEMINI_API_KEY = GeminiApiKeyProvider().getKey()
        this.SERVER_KEY = RssHubApiKeyProvider().getKey()
        this.MINIFLUX_KEY = MinifluxApiKeyProvider().getKey()
        this.PROXY_ADDRESS = ProxyAddressProvider().getKey()
        this.HUB_ADDRESS = RSSHubAddressProvider().getKey()
        this.MINIFLUX_ADDRESS = MinifluxAddressProvider().getKey()

        setContext(appContext)

        val settingsFlow = settingsManager.settings

        fun <T> createSettingFlow(mapBlock: (AppSettings) -> T, default: T): StateFlow<T> {
            return settingsFlow
                .map {
                    try {
                        mapBlock(it)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error mapping setting, using default: $default", e)
                        default
                    }
                }
                .catch { e ->
                    Log.e(TAG, "Critical error in settings flow, emitting default: $default", e)
                    emit(default)
                }
                .stateIn(externalScope, SharingStarted.Eagerly, default)
        }

//        updatingState = createSettingFlow({ it.updatingState }, "off")
//        updatingProgress = createSettingFlow({ it.updatingProgress }, 0f)
        lastTitlesUpdate = createSettingFlow({ it.lastTitlesUpdate }, 0L)
        bannedNewsFlow = createSettingFlow({ it.bannedNews }, emptySet())

        darkTheme = createSettingFlow({ it.darkTheme }, DarkTheme.SYSTEM)
        appTheme = createSettingFlow({ it.appTheme }, AppTheme.DEFAULT)
        expandSources = createSettingFlow({ it.expandSources }, false)
        titleSorting = createSettingFlow({ it.titlesSorting }, TitleSorting.OLDEST)
        titlesNum = createSettingFlow({ it.titlesNum }, HeadersNum.NUM_10)
        titlesPeriod = createSettingFlow({ it.titlesPeriod }, TitlesPeriod.HRS_24)
        titlesKeeping = createSettingFlow({ it.titlesKeeping }, TitlesKeeping.DAYS_1)
        titlesAutoUpdateFrequency = createSettingFlow({ it.titlesAutoUpdateFrequency }, AutoUpdateFrequency.FREQ_24)
        sanitizeCopiedText = createSettingFlow( { it.sanitizeCopiedText }, false)
        saveUnreadTitles = createSettingFlow(  { it.saveUnreadTitles }, false)
        enableUpdateNotification = createSettingFlow({ it.enableUpdateNotification }, false)
        llmModel = createSettingFlow({ it.llmModel }, GeminiModelOption.FLASH_LATEST)

        userApiKey = createSettingFlow({
            it.userApiKey.ifBlank { DEFAULT_GEMINI_API_KEY }
        }, DEFAULT_GEMINI_API_KEY)

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

        parserBatchSize = createSettingFlow({ it.parserBatchSize }, 50)

        val defaultLang = getStringResource(R.string.current_language)
        currentLanguage = createSettingFlow({ it.currentLanguage }, defaultLang)

        lastError = settingsFlow.map { settings ->
            val saved = settings.lastError
            if (saved != null) {
                try {
                    val msg = saved.message.takeIf { it.isNotBlank() } ?: "Unknown saved error"
                    val type = saved.type
                    SummarizationResult.Failure(type = type, cause = Exception(msg))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore lastError: ${e.message}")
                    null
                }
            } else {
                null
            }
        }
            .catch { e ->
                Log.e(TAG, "Critical error in lastError flow", e)
                emit(null)
            }
            .flowOn(Dispatchers.IO)
            .stateIn(externalScope, SharingStarted.Eagerly, null)

        sources = sourceDao.getAllSourcesFlow()
            .map { entities ->
                entities.map {
                    RSS(
                        it.id, it.customName ?: it.originalName, it.originalName,
                        feedUrl = it.feedUrl,
                        websiteUrl = formatWebsiteUrl(it.websiteUrl),
                        sourceType = SourceType.fromId(it.sourceType),
                        errCount = it.errCount,
                        lastUpdated = it.lastSyncTime,
                        avatarUrl = null
                    ) }
            }
            .flowOn(Dispatchers.IO)

        titles = titleDao.getAllTitlesFlow()
            .map { entities ->
                coroutineScope {
                    entities.map { entity ->
                        async {
                            val sourcesList = titleDao.getSourcesForTitleFlow(entity.id).first()
                            val messagesList = titleDao.getMessagesForTitleFlow(entity.id).first()
                            val sourcesStr = sourcesList.distinct().joinToString(", ") { it.customName ?: it.originalName }
                            val idsStr = messagesList.joinToString(", ") { it.id.toString() }

                            val parent = titleDao.getParentTitle(entity.id)
                            val child = titleDao.getChildTitle(entity.id)
                            val depth = calculateDepth(entity)
                            val mediaUrlsList = messagesList.flatMap { message ->
                                message.mediaUrls.map { url ->
                                    MediaWithSource(
                                    mediaLink = url,
                                    message = Message(
                                        id = message.id,
                                        time = message.pubTime,
                                        link = message.link,
                                        source = getSource(message.sourceId),
                                        originalText = "",
                                        cleanText = ""
                                    )
                                ) }
                            }.distinct()

                            Title(
                                id = entity.id,
                                title = entity.title,
                                summary = entity.summary,
                                eventTime = entity.eventTime,
                                updateTime = entity.updateTime,
                                status = entity.status,
                                isRead = entity.isRead,
                                sources = sourcesStr,
                                ids = idsStr,
                                keywords = entity.keywords,
                                parentId = parent?.id,
                                childId = child?.id,
                                relatedTitle = child?.title,
                                relatedSnippet = "${TextSanitizer.sanitize(child?.summary ?: "").take(120)}...",
                                storyDepth = depth,
                                mediaUrls = mediaUrlsList
                            )
                        }
                    }.awaitAll()
                }
            }
            .flowOn(Dispatchers.Default)

        isInitialized = true
    }

    private suspend fun calculateDepth(entity: TitleEntity): Int {
        var current: TitleEntity? = entity
        var depth = 0
        while (depth < 5) {
            val parent = current?.let { titleDao.getParentTitle(it.id) }
            if (parent == null) break
            current = parent
            depth++
        }
        return depth
    }

    val darkThemeList: List<DarkTheme> get() = DarkTheme.entries
    val appThemeList: List<AppTheme> get() = AppTheme.entries
    val titleSortingList: List<TitleSorting> get() = TitleSorting.entries
    val headersNumList: List<HeadersNum> get() = HeadersNum.entries
    val titlesPeriodList: List<TitlesPeriod> get() = TitlesPeriod.entries
    val titlesKeepingList: List<TitlesKeeping> get() = TitlesKeeping.entries
    val autoUpdateFrequencyList: List<AutoUpdateFrequency> get() = AutoUpdateFrequency.entries

    val geminiModelsList: List<GeminiModelOption> get() = GeminiModelOption.entries
    val defaultModel: GeminiModelOption get() = GeminiModelOption.FLASH_LITE_LATEST

    private val _stoppedManually = MutableStateFlow(false)
    var stoppedManually: StateFlow<Boolean> = _stoppedManually.asStateFlow()

    suspend fun checkGeminiApiKey(key: String): Boolean {
        return validateGeminiKey(key, PROXY_ADDRESS, SERVER_KEY, proxyEnabled.value)
    }

    fun addSource(context: Context, name: String, link: String) {
        externalScope.launch(Dispatchers.IO) {
            SourceResolver.resolveSourceDetails(link).let { source ->
                when (source) {
                    null -> return@let
                    else -> {
                        val entity = SourceEntity(
                            originalName = source.name,
                            customName = if (source.name != name) name else null,
                            websiteUrl = source.websiteUrl,
                            feedUrl = source.feedUrl,
                            sourceType = source.type.id,
                            lastSyncTime = 0,
                            errCount = 0,
                            lastErrMsg = null
                        )
                        sourceDao.insert(entity)
                    }
                }
            }
        }

        AlarmScheduler.cancel(context, true)
        setParserUpdate(context, intervalMin = rssUpdateInterval.value.toLong(), isImmediateSetup = true)
    }

    suspend fun getSource(id: Long): RSS? = withContext(Dispatchers.IO) {
        val source = sourceDao.getSourceById(id)
        if (source != null) RSS(
            source.id,
            source.customName ?: source.originalName,
            source.originalName,
            source.feedUrl,
            source.websiteUrl,
            SourceType.fromId(source.sourceType),
            errCount = source.errCount,
            lastUpdated = source.lastSyncTime,
            avatarUrl = null
        ) else null
    }

    fun getSourcesWithAvatars(): Flow<List<RSS>> = channelFlow {
        val semaphore = Semaphore(3)

        sourceDao.getAllSourcesFlow()
            .combine(resolvedAvatars) { entities, avatars ->
                entities.map { entity ->
                    val fetchedAvatar = avatars[entity.id]?.takeIf { it.isNotEmpty() }
                    RSS(
                        id = entity.id,
                        currentName = entity.customName ?: entity.originalName,
                        originalName = entity.originalName,
                        feedUrl = entity.feedUrl,
                        websiteUrl = formatWebsiteUrl(entity.websiteUrl),
                        sourceType = SourceType.fromId(entity.sourceType),
                        errCount = entity.errCount,
                        lastUpdated = entity.lastSyncTime,
                        avatarUrl = fetchedAvatar
                    )
                }
            }
            .collect { currentList ->
                send(currentList)

                currentList.forEach { item ->
                    if (!resolvedAvatars.value.containsKey(item.id)) {
                        resolvedAvatars.update { it + (item.id to "") }

                        launch(Dispatchers.IO) {
                            semaphore.withPermit {
                                val avatar = SourceResolver.resolveSourceDetails(item.websiteUrl)?.avatarUrl
                                if (avatar != null) {
                                    resolvedAvatars.update { it + (item.id to avatar) }
                                }
                                delay(100L)
                            }
                        }
                    }
                }
            }
    }

    fun deleteSource(id: Long) {
        externalScope.launch(Dispatchers.IO) {
            sourceDao.deleteById(id)
            resolvedAvatars.update { it - id }
        }
    }

    fun changeSource(id: Long, newName: String) {
        externalScope.launch(Dispatchers.IO) {
            sourceDao.updateCustomName(id, newName)
        }
    }

    fun setSourceSummarizingSyncTime(sourceId: Long? = null) {
        externalScope.launch(Dispatchers.IO) {
            if (sourceId == null) sourceDao.updateAllSummarizingSyncToLastSync()
            else sourceDao.updateSummarizingSyncToLastSync(sourceId)
        }
    }

    fun startTitlesUpdate(context: Context) {
        setTitlesUpdate(context)
    }

    fun manuallyLinkTopics(childId: Long, parentId: Long) {
        externalScope.launch(Dispatchers.IO) {
            try {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT OR IGNORE INTO title_related_map (title_id_1, title_id_2) VALUES ($childId, $parentId)"
                )
                Log.d("Mews", "Успешно связали новость $childId с родителем $parentId")
            } catch (e: Exception) {
                Log.e("Mews", "Ошибка при связывании", e)
            }
        }
    }

    fun markTitleAsRead(id: Long, read: Boolean = true) {
        val statusInt = if (read) 1 else 0
        externalScope.launch(Dispatchers.IO) {
            titleDao.updateReadStatus(id, statusInt)
        }
    }

    fun delTitles(time: Long? = null, onlyRead: Boolean = false) {
        externalScope.launch(Dispatchers.IO) {
            if (!onlyRead) titleDao.deleteBeforeUpdateTime(time ?: 0)
            else titleDao.deleteReadItemsBeforeUpdateTime(time ?: 0)
        }
    }

    suspend fun addMessage(
        sourceId: Long,
        messageTime: Long,
        link: String,
        messageText: String,
        title: String = "",
        mediaUrls: List<String> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        val existing = messageDao.getMessageByLink(link)
        if (existing != null) {
            return@withContext existing.id
        }

        val entity = MessageEntity(
            sourceId = sourceId,
            link = link,
            pubTime = messageTime,
            title = title,
            originalText = messageText,
            cleanText = messageText,
            isDuplicate = false,
            isRead = false,
            factCheck = null,
            mediaUrls = mediaUrls
        )
        messageDao.insert(entity)
    }

    suspend fun messageTimeKill(timeSeconds: Long): Int = withContext(Dispatchers.IO) {
        val killTimeMs = System.currentTimeMillis() - (timeSeconds * 1000)
        messageDao.deleteBeforeTime(killTimeMs)
    }

    suspend fun getMessages(ids: String): List<Message>? = withContext(Dispatchers.IO) {
        val idList = ids.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (idList.isEmpty()) return@withContext null

        val targetMsgs = messageDao.getMessagesByIds(idList)

        val result = targetMsgs.map { msg ->
            val source = getSource(msg.sourceId)
            Message(msg.id, msg.pubTime, msg.link, source, msg.originalText, msg.cleanText)
        }
        result.ifEmpty { null }
    }

    suspend fun getMessagesList(timeSeconds: Long? = null): List<Message> = withContext(Dispatchers.IO) {
        val entities = if (timeSeconds != null) {
            val timeMs = System.currentTimeMillis() - (timeSeconds * 1000)
            messageDao.getMessagesAfterTimeOneShot(timeMs)
        } else {
            messageDao.getAllMessagesOneShot()
        }

        entities.map { msg ->
            val source = getSource(msg.sourceId)
            Message(msg.id, msg.pubTime, msg.link, source, msg.originalText, msg.cleanText)
        }
    }

    suspend fun getUniqueMessagesList(timeMs: Long? = null): List<Message> = withContext(Dispatchers.IO) {
        val entities = if (timeMs != null) {
            messageDao.getUniqueMessagesAfterTimeOneShot(timeMs)
        } else {
            messageDao.getUniqueMessagesForSummarizing(lastTitlesUpdate.first())
        }

        entities.map { msg ->
            val source = getSource(msg.sourceId)
            Message(msg.id, msg.pubTime, msg.link, source, "", msg.cleanText)
        }
    }

    suspend fun getAllUniqueMessages(): List<Message> = withContext(Dispatchers.IO) {
        val entities = messageDao.getAllUniqueMessagesOneShot()

        entities.map { msg ->
            val source = getSource(msg.sourceId)
            Message(msg.id, msg.pubTime, msg.link, source, "", msg.cleanText)
        }
    }

    suspend fun getTitlesUpdateTimeMarks(): List<Long> = withContext(Dispatchers.IO) {
        return@withContext titleDao.getAllUpdateTimes().distinct().sortedDescending()
    }

    suspend fun getTargetWindowTimeMark(windowMs: Long = 4 * 3600 * 1000L): Long? = withContext(
        Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        if ((currentTime - lastTitlesUpdate.first()) >= windowMs) return@withContext null
        return@withContext getTitlesUpdateTimeMarks().firstOrNull { currentTime - it >= windowMs }
    }

    suspend fun getAllUsedMessageIds(windowMs: Long? = null): List<Long> = withContext(Dispatchers.IO) {
        val time = if (windowMs == null) null else System.currentTimeMillis() - windowMs
        return@withContext messageDao.getAllUsedMessages(time).distinct()
    }

    suspend fun findLastChild(titleId: Long): Long = withContext(Dispatchers.IO) {
        var selectedTopic = titleId
        var childTopic = titleDao.getChildTitle(selectedTopic)?.id
        var depth = 0
        val maxDepth = 15
        while (childTopic != null && depth <= maxDepth) {
            depth++
            selectedTopic = childTopic
            childTopic = titleDao.getChildTitle(selectedTopic)?.id
        }
        return@withContext selectedTopic
    }

    suspend fun getCleanTextsInWindow(timeStart: Long, timeEnd: Long): List<String> = withContext(Dispatchers.IO) {
        messageDao.getCleanTextsInWindow(timeStart, timeEnd)
    }

    suspend fun getMessageByLink(link: String): Message? = withContext(Dispatchers.IO) {
        when (val mess = messageDao.getMessageByLink(link)) {
            null -> return@withContext null
            else -> {
                Message(
                    mess.id,
                    mess.pubTime,
                    mess.link,
                    getSource(mess.sourceId),
                    mess.originalText,
                    mess.cleanText
                )
            }
        }
    }

//    suspend fun getProcessedMessageIds(sinceMs: Long): Set<Long> = withContext(Dispatchers.IO) {
//        val recentTitles = titleDao.getChildfreeTitlesFlow().first()
//            .filter { titleDao.getChildTitle(it.id) == null && it.eventTime >= sinceMs && it.status != TitleStatus.PROCESSING.statusId }
//        val ids = mutableSetOf<Long>()
//        for (title in recentTitles) {
//            ids.addAll(titleDao.getMessageIdsForTitle(title.id))
//        }
//        ids
//    }

    suspend fun addTitle(
        newTimeVal: Long,
        newTitle: String,
        summary: String,
        messageIds: List<Long>,
        status: TitleStatus = TitleStatus.DEFAULT,
        keywords: List<String> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        val titleEntity = TitleEntity(
            title = newTitle,
            summary = summary,
            eventTime = newTimeVal,
            updateTime = newTimeVal,
            status = status.statusId,
            isRead = false,
            isPinned = false,
            importanceWeight = 0,
            keywords = keywords
        )
        val titleId = titleDao.insert(titleEntity)

        messageIds.distinct().forEach { msgId ->
            titleDao.insertTitleMessageMap(TitleMessageMap(titleId, msgId))
        }

        titleId
    }

    suspend fun updateTitle(
        id: Long,
        newEventTime: Long,
        newTitle: String,
        summary: String,
        status: Int = 0,
        parentId: Long? = null
    ) = withContext(Dispatchers.IO) {
        val existing = titleDao.getTitleById(id) ?: return@withContext
        val updatedEntity = existing.copy(
            title = newTitle,
            summary = summary,
            eventTime = newEventTime,
            status = status
        )
        titleDao.update(updatedEntity)

        if (parentId != null) {
            try {
                titleDao.insertRelatedMapSafe(id, parentId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert title relation", e)
            }
        }
    }

    suspend fun getTitlesWithStatus(processingStatusId: Int): List<NewsSummarizer.Topics> = withContext(Dispatchers.IO) {
        val validTitles = titleDao.getTitlesNotProcessing(processingStatusId)

        validTitles.map { entity ->
            val messageIds = titleDao.getMessageIdsForTitle(entity.id)
            NewsSummarizer.Topics(
                title = entity.title,
                ids = messageIds,
                id = entity.id
            )
        }
    }

    suspend fun getRecentTitlesForStorylines(sinceMs: Long): List<TitleEntity> = withContext(Dispatchers.IO) {
        titleDao.getChildfreeTitlesFlow().first()
            .filter { titleDao.getChildTitle(it.id) == null && it.eventTime >= sinceMs && it.status != TitleStatus.PROCESSING.statusId }
    }

    suspend fun getTitlesCount(): Int = withContext(Dispatchers.IO) {
        titleDao.getAllTitlesFlow().first().size
    }

    suspend fun deleteTitleById(id: Long) = withContext(Dispatchers.IO) {
        titleDao.deleteById(id)
    }

    suspend fun getRssName(link: String): String? {
        return SourceResolver.resolveSourceDetails(link)?.name
    }

    fun setCurrentTab(newTab: TabScreen) { _selectedTab.value = newTab }

    fun setDarkTheme(newValue: DarkTheme) = updateSetting { it.copy(darkTheme = newValue) }
    fun setAppTheme(newValue: AppTheme) = updateSetting { it.copy(appTheme = newValue) }
    fun setExpandSources(newValue: Boolean) = updateSetting { it.copy(expandSources = newValue) }
    fun setTitleSorting(newValue: TitleSorting) = updateSetting { it.copy(titlesSorting = newValue) }
    fun setTitlesNum(newValue: HeadersNum) = updateSetting { it.copy(titlesNum = newValue) }
    fun setTitlesPeriod(newValue: TitlesPeriod) = updateSetting { it.copy(titlesPeriod = newValue) }
    fun setTitlesKeeping(newValue: TitlesKeeping) = updateSetting { it.copy(titlesKeeping = newValue) }
    fun setLlmModel(newValue: GeminiModelOption) = updateSetting { it.copy(llmModel = newValue) }
    fun setTitlesAutoUpdateFrequency(newValue: AutoUpdateFrequency) = updateSetting { it.copy(titlesAutoUpdateFrequency = newValue) }
    fun setUserApiKey(newValue: String) = updateSetting { it.copy(userApiKey = newValue) }
    fun setShowDates(newValue: Boolean) = updateSetting { it.copy(showDates = newValue) }
    fun setSanitizeCopiedText(newValue: Boolean) = updateSetting { it.copy(sanitizeCopiedText = newValue) }
    fun setSaveUnreadTitles(newValue: Boolean) = updateSetting { it.copy(saveUnreadTitles = newValue) }
    fun setEnableUpdateNotification(newValue: Boolean) = updateSetting { it.copy(enableUpdateNotification = newValue) }

    fun setRssUpdateInterval(context: Context, newValue: Int) {
        externalScope.launch {
            settingsManager.updateSettings { it.copy(rssUpdateInterval = newValue) }
            AlarmScheduler.cancel(context, true)
            setParserUpdate(context, intervalMin = newValue.toLong())
        }
    }

    fun setLastRssUpdate(newValue: Long) = updateSetting { it.copy(lastRssUpdate = newValue) }
    fun setProxyEnabled(newValue: Boolean) = updateSetting { it.copy(enableProxy = newValue) }
    fun setLastTitlesUpdate(newValue: Long) = updateSetting { it.copy(lastTitlesUpdate = newValue) }
    fun setUpdatingTitles(newValue: Boolean) { _updatingTitles.value = newValue }
    fun setUpdatingState(newValue: UpdatingState) { _updatingState.value = newValue }
    fun setUpdatingProgress(newValue: Float) { _updatingProgress.value = newValue.coerceIn(0f, 1f) }
    fun setBannedNews(newValue: Set<String>) = updateSetting { it.copy(bannedNews = newValue) }

    fun addBannedNew(newValue: String) {
        if (!bannedNewsFlow.value.contains(newValue) && bannedNewsFlow.value.size <= 40) {
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
            settingsManager.updateSettings { it.copy(notificationsGranted = newValue, titlesAutoUpdate = if (!newValue) false else it.titlesAutoUpdate,
            enableUpdateNotification = if (!newValue) false else it.enableUpdateNotification) }
        }
    }

    fun setCurrentLanguage(newValue: String) = updateSetting { it.copy(currentLanguage = newValue) }
    fun setParserBatchSize(newValue: Int) = updateSetting { it.copy(parserBatchSize = newValue) }

    fun setStoppedManually(value: Boolean) {
        _stoppedManually.value = value
        _updatingProgress.value = 0f
    }

    fun saveLastError(failure: SummarizationResult.Failure) {
        if (failure.type == SummarizationErrorType.JOB_CANCELLED && _stoppedManually.value) _stoppedManually.value = false
        else externalScope.launch { settingsManager.saveLastError(failure) }
    }

    fun clearError() {
        externalScope.launch { settingsManager.clearLastError() }
    }

    fun cancelTitlesAutoUpdates(context: Context) {
        AlarmScheduler.cancel(context)
    }

    fun getAppContext(): Context {
        return _context.value ?: throw IllegalStateException("MewsRepository not initialized via initialize()")
    }

    fun planTitlesUpdate(context: Context, explicitTimeMins: Int? = null, alarmUpdateValue: Boolean? = null) {
        val alarmUpdatePlanned = alarmUpdateValue ?: titlesAlarmUpdate.value
        if (alarmUpdatePlanned) {
            val updateFrequencyHours = titlesAutoUpdateFrequency.value.num

            val updateTimeMins = explicitTimeMins ?: titlesAlarmTimeMins.value

            val nextRunTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, updateTimeMins / 60)
                set(Calendar.MINUTE, updateTimeMins % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

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

    suspend fun getSourcesQueue(): List<SourceEntity> = withContext(Dispatchers.IO) {
        sourceDao.getAllSourcesFlow().first()
            .filter { it.errCount < 3 }
            .sortedBy { it.lastSyncTime }
    }

    suspend fun getMaxPubTimeForSource(sourceId: Long): Long? = withContext(Dispatchers.IO) {
        messageDao.getMaxPubTimeForSource(sourceId)
    }

    suspend fun getAllSourceEntities(): List<SourceEntity> = withContext(Dispatchers.IO) {
        sourceDao.getAllSourcesFlow().first()
    }

    suspend fun updateSourceEtag(sourceId: Long, etag: String?) = withContext(Dispatchers.IO) {
        sourceDao.updateEtag(sourceId, etag)
    }

    suspend fun insertBatchAndUpdateSourceTime(
        messages: List<MessageEntity>,
        sourceId: Long,
        syncTime: Long
    ) = withContext(Dispatchers.IO) {
        database.insertBatchAndUpdateSourceTime(messages, sourceId, syncTime)
    }

    suspend fun incrementErrorCount(sourceId: Long) = withContext(Dispatchers.IO) {
        sourceDao.incrementErrorCount(sourceId)
    }

    suspend fun resetErrorCount(sourceId: Long) = withContext(Dispatchers.IO) {
        sourceDao.resetErrorCount(sourceId)
    }

    fun resetAllSourceErrors() {
        externalScope.launch(Dispatchers.IO) {
            sourceDao.resetAllErrorCounts()
        }
    }

    private fun formatWebsiteUrl(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }
}