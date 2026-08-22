package com.rds.mews.viewmodels

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.collection.intListOf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.rds.mews.MainActivity
import com.rds.mews.R
import com.rds.mews.core.TelegramRssClient
import com.rds.mews.localcore.MediaWithSource
import com.rds.mews.localcore.SourceMessages
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.localcore.TimeDate
import com.rds.mews.localcore.Title
import com.rds.mews.localcore.TitleCardStates
import com.rds.mews.localcore.TitleSorting
import com.rds.mews.localcore.TitleStatus
import com.rds.mews.localcore.TitlesGroupState
import com.rds.mews.localcore.UpdatingState
import com.rds.mews.localcore.cancelTitlesUpdate
import com.rds.mews.localcore.formatUpdateTime
import com.rds.mews.localcore.getStringsFromDate
import com.rds.mews.localcore.requestNotificationPermission
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.settings_manager.SummarizationErrorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.collections.filter
import kotlin.collections.map
import kotlin.time.Duration.Companion.milliseconds

class BlitzViewModel(
    private val application: Application,
    private val repository: MewsRepository
): AndroidViewModel(application) {
    private val _scrollEvents = Channel<TitlesScrollEvent>(Channel.CONFLATED)
    val scrollEvents = _scrollEvents.receiveAsFlow()

    private val todayDateFlow = callbackFlow {
        trySend(LocalDate.now())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(LocalDate.now())
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }

        ContextCompat.registerReceiver(
            application.applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        awaitClose {
            application.applicationContext.unregisterReceiver(receiver)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    private val _greetingMessages = MutableStateFlow<List<Title>>(emptyList())
    private val _titles = MutableStateFlow<List<Title>>(emptyList())
    val titles = _titles.asStateFlow()

    val isRefreshing: StateFlow<Boolean> = repository.updatingTitles
    val titleSorting: StateFlow<TitleSorting> = repository.titleSorting
    val updatingState: StateFlow<UpdatingState> = repository.updatingState
    val updatingProgress: StateFlow<Float> = repository.updatingProgress

    val lastUpdated: StateFlow<Long> = repository.lastTitlesUpdate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _errState = MutableStateFlow<SummarizationResult.Failure?>(null)
    val errState = _errState.asStateFlow()

    val groupedTitles = combine(
        titles,
        todayDateFlow,
        titleSorting
    ) { titles, today, titleSorting ->
        val greetings = titles.filter { it.id < 0L }
        val actuals = titles.filter { it.id >= 0L }

        val filteredActuals = when (titleSorting) {
            TitleSorting.NEWEST -> actuals.reversed().filter { it.status == TitleStatus.DEFAULT.statusId }
            else -> actuals.filter { it.status == TitleStatus.DEFAULT.statusId }
        }

        val finalFiltered = if (greetings.isNotEmpty()) greetings else filteredActuals

        finalFiltered.groupBy { title ->
            getDateFromUnix(title.eventTime, today).copy(time = "00:00")
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    private val _showEmptyMessage = MutableStateFlow(false)
    val showEmptyMess: StateFlow<Boolean> = _showEmptyMessage

    private val _isIndicatorCollapsed = MutableStateFlow(false)
    val isIndicatorCollapsed: StateFlow<Boolean> = _isIndicatorCollapsed

    private val _isBlitzActive = MutableStateFlow(false)
    val isBlitzActive: StateFlow<Boolean> = _isBlitzActive.asStateFlow()

    private val _titleCardStates = MutableStateFlow<Set<TitleCardStates>>(emptySet())
    val titleCardStates: StateFlow<Set<TitleCardStates>> = _titleCardStates.asStateFlow()

    private val _dynamicMediaUrls = MutableStateFlow<Map<Long, List<MediaWithSource>>>(emptyMap())
    val dynamicMediaUrls: StateFlow<Map<Long, List<MediaWithSource>>> = _dynamicMediaUrls.asStateFlow()

    val sanitizeCopiedText: StateFlow<Boolean> = repository.sanitizeCopiedText

    fun toggleBlitzActive() {
        _isBlitzActive.value = !_isBlitzActive.value
    }

    fun toggleEmptyMess(newValue: Boolean) {
        _showEmptyMessage.value = newValue
    }

    fun refreshTitles() {
        repository.startTitlesUpdate(application)
    }

    fun changeIndicatorCollapsed() {
        _isIndicatorCollapsed.value = !_isIndicatorCollapsed.value
    }

    fun scrollToTop() {
        _scrollEvents.trySend(TitlesScrollEvent.ScrollToTop)
    }

    fun scrollToItem(index: Int) {
        val targetIndex = (index - 2).coerceIn(0, Int.MAX_VALUE)
        _scrollEvents.trySend(TitlesScrollEvent.ScrollToItem(targetIndex, animated = false))
    }

    fun showGreeting(context: Context) {
        viewModelScope.launch {
            val list = emptyList<Title>().toMutableList()
            list += Title(
                id = -1L,
                eventTime = System.currentTimeMillis(),
                title = context.getString(R.string.greeting_1),
                summary = "",
                sources = "",
                ids = ""
            )
            delay(300L.milliseconds)
            _greetingMessages.value = list.toList()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun handleErrorAction(clipboardManager: ClipboardManager, activity: MainActivity) {
        val err = errState.value ?: return

        when (err.type) {
            in listOf(
                SummarizationErrorType.EXTRACT_TOPICS_FAILED,
                SummarizationErrorType.SUMMARIZE_TOPICS_FAILED,
                SummarizationErrorType.EMPTY_ANSWER,
                SummarizationErrorType.NETWORK_TIMEOUT,
                SummarizationErrorType.FILTER_FAILED,
                SummarizationErrorType.UNPROCESSED_ITEMS
            ) -> refreshTitles()
            SummarizationErrorType.JOB_CANCELLED -> requestNotificationPermission(activity)
            SummarizationErrorType.UNKNOWN_ERROR -> {
                val copiedText = "${err.cause}"
                clipboardManager.setText(AnnotatedString(copiedText))
            }
            else -> { }
        }
    }

    fun markTitleAsPinned(id: Long, pinned: Boolean) {
        repository.markTitleAsPinned(id, pinned)
    }

    fun markTitleAsRead(id: Long, read: Boolean = true) {
        repository.markTitleAsRead(id, read)
        _titleCardStates.update { currentSet ->
            currentSet.map {
                if (it.id == id) { it.copy(read = read) }
                else it
            }.toSet()
        }
    }

    fun stopTitlesUpdate(context: Context) {
        viewModelScope.launch { cancelTitlesUpdate(context) }
    }

    fun clearErr() {
        repository.clearError()
    }

    fun setCurrentTitleImage(id: Long, image: Int) {
        _titleCardStates.update { currentSet ->
            currentSet.map {
                if (it.id == id) { it.copy(currentImage = image) }
                else it
            }.toSet()
        }
    }

    fun setFullscreenImageForTitle(id: Long, state: Boolean) {
        _titleCardStates.update { currentSet ->
            currentSet.map {
                if (it.id == id) { it.copy(fullscreenImage = state) }
                else it
            }.toSet()
        }
    }

    fun lastTitlesUpdateExists(): Boolean {
        return repository.lastTitlesUpdate.value != 0L
    }

    fun getDateFromUnix(timeUnix: Long, today: LocalDate = LocalDate.now()): TimeDate {
        val fPair = formatUpdateTime(timeUnix, today = today)

        return when (fPair.first) {
            0 -> {
                val instant = Instant.ofEpochMilli(timeUnix)
                val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                val dateFormatter = DateTimeFormatter.ofPattern("d.M")
                val dateString = dateTime.format(dateFormatter)

                val ints = getStringsFromDate(dateString) ?: intListOf(1, 1)

                TimeDate(
                    number = ints[1],
                    date = ints[0],
                    time = fPair.second
                )
            }
            else -> TimeDate(
                date = fPair.first,
                time = fPair.second
            )
        }
    }

    fun loadDynamicMediaUrls(titleId: Long, fromZero: Boolean = false) {
        if (fromZero) _dynamicMediaUrls.update { currentMap ->
            currentMap - titleId
        }
        else if (_dynamicMediaUrls.value.containsKey(titleId)) return

        val targetTitle = _titles.value.find { it.id == titleId } ?: return
        val mediaObjects = targetTitle.mediaUrls

        val targetState = _titleCardStates.value.find { it.id == titleId }
        val sources = targetState?.sources
        val allMessages = sources?.flatMap { it.messages } ?: emptyList()
        val telegramLinks = allMessages.filter { it.link.contains("t.me") }

        val validOriginals = mediaObjects.map { media ->
            MediaWithSource(
                mediaLink = media.mediaLink.substringBefore("?"),
                message = media.message
            )
        }.filter { media ->
            val cleanUrl = media.mediaLink.lowercase()
            !cleanUrl.endsWith(".mp4") && !cleanUrl.endsWith(".webm") &&
                    !cleanUrl.endsWith(".mov") && !cleanUrl.endsWith(".mkv") &&
                    !cleanUrl.contains("telesco.pe") && !cleanUrl.contains("cdn-telegram.org")
        }.distinctBy { it.mediaLink }

        if (telegramLinks.isEmpty()) {
            _dynamicMediaUrls.update { it + (titleId to validOriginals) }
            return
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val scraper = TelegramRssClient(repository.proxyEnabled.value)
                telegramLinks.forEach { message ->
                    launch {
                        try {
                            val scrapedUrls = scraper.scrapeEmbedMedia(message.link)
                                .map { MediaWithSource(it, message) }
                                .filter { media ->
                                    val cleanUrl = media.mediaLink.lowercase()
                                    !cleanUrl.endsWith(".mp4") && !cleanUrl.endsWith(".webm") &&
                                            !cleanUrl.endsWith(".mov") && !cleanUrl.endsWith(".mkv")
                                }

                            if (scrapedUrls.isNotEmpty()) {
                                _dynamicMediaUrls.update { currentMap ->
                                    val existingList = currentMap[titleId] ?: validOriginals
                                    val updatedList = (existingList + scrapedUrls).distinctBy { it.mediaLink }
                                    currentMap + (titleId to updatedList)
                                }
                            } else {
                                _dynamicMediaUrls.update { currentMap ->
                                    if (!currentMap.containsKey(titleId)) currentMap + (titleId to validOriginals) else currentMap
                                }
                            }
                        } catch (e: Exception) {
                            _dynamicMediaUrls.update { currentMap ->
                                if (!currentMap.containsKey(titleId)) currentMap + (titleId to validOriginals) else currentMap
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            combine(
                repository.blitzTitles.distinctUntilChanged(),
                _greetingMessages
            ) { titles, greetings ->
                Pair(titles, greetings)
            }.collect { (titleListFromDb, greetingList) ->
                val actualTitles = titleListFromDb.filter { it.status == TitleStatus.DEFAULT.statusId }

                val combinedTitles = greetingList + actualTitles
                _titles.value = combinedTitles

                _titleCardStates.update { currentStates ->
                    val oldStatesMap = currentStates.associateBy { it.id }

                    combinedTitles.map { title ->
                        val oldState = oldStatesMap[title.id]
                        val messages = if (title.ids.isBlank()) null else MewsRepository.getMessages(title.ids)

                        val newSources = if (messages == null) null else {
                            val groupedMessages = messages.groupBy { it.source }
                            val sourceMessages = mutableListOf<SourceMessages>()

                            groupedMessages.forEach { (source, msgs) ->
                                sourceMessages.add(SourceMessages(source, false, msgs))
                            }
                            sourceMessages.toList()
                        }

                        TitleCardStates(
                            id = title.id,
                            expanded = oldState?.expanded ?: false,
                            read = title.isRead,
                            currentPage = oldState?.currentPage ?: 0,
                            sources = newSources,
                            currentImage = oldState?.currentImage ?: 0,
                            fullscreenImage = oldState?.fullscreenImage ?: false
                        )
                    }.toSet()
                }
            }
        }

        viewModelScope.launch {
            repository.lastError.collect { error ->
                _errState.value = error
            }
        }
    }
}

class BlitzViewModelFactory(private val application: Application) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BlitzViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BlitzViewModel(application, MewsRepository) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}