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
import com.rds.mews.localcore.Message
import com.rds.mews.localcore.SourceMessages
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.localcore.SummarizationErrorType
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.localcore.TimeDate
import com.rds.mews.localcore.Title
import com.rds.mews.localcore.TitleCardStates
import com.rds.mews.localcore.TitlesGroupState
import com.rds.mews.localcore.formatUpdateTime
import com.rds.mews.localcore.getStringsFromDate
import com.rds.mews.localcore.requestNotificationPermission
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.rds.mews.R
import kotlinx.coroutines.delay

class TitlesViewModel(
    private val application: Application,
    private val repository: MewsRepository
): AndroidViewModel(application) {
    private val _scrollEvents = Channel<TitlesScrollEvent>(Channel.CONFLATED)
    val scrollEvents = _scrollEvents.receiveAsFlow()

    private val workManager = WorkManager.getInstance(application)
    val workInfo: StateFlow<WorkInfo?> = workManager
        .getWorkInfosForUniqueWorkFlow("titles_update_work")
        .map { it.firstOrNull() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    private val _titles = MutableStateFlow<List<Title>>(emptyList())
    val titles = _titles.asStateFlow()

    val isRefreshing: StateFlow<Boolean> = repository.updatingTitles
    val updatingState: StateFlow<String?> = repository.updatingState
    val updatingProgress: StateFlow<Float> = repository.updatingProgress

    val showDates: StateFlow<Boolean> = repository.showDates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val innerTimestamps: StateFlow<Boolean> = repository.innerTimestamps.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), false)
    val showSnippets: StateFlow<Boolean> = repository.showSnippets.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), false)

    val lastUpdated: StateFlow<Long> = repository.lastTitlesUpdate
        .stateIn(viewModelScope,
            SharingStarted.WhileSubscribed(5000), 0)

    private val _errState = MutableStateFlow<SummarizationResult.Failure?>(null)
    val errState = _errState.asStateFlow()

    val groupedTitles = combine(
        titles,
        todayDateFlow
    ) { titles, today ->
        titles.groupBy { title ->
            getDateFromUnix(title.time, today).copy(time = "00:00")
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    private val _groupStates = MutableStateFlow<Map<TimeDate, Boolean>>(emptyMap())
    val groupStates: StateFlow<List<TitlesGroupState>> = combine(
        groupedTitles,
        _groupStates
    ) { titleMap, groupMap  ->
        titleMap.map { (key, _) ->
            TitlesGroupState(key, groupMap[key] ?: true)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _showEmptyMessage = MutableStateFlow(false)
    val showEmptyMess: StateFlow<Boolean> = _showEmptyMessage

    private val _isIndicatorCollapsed = MutableStateFlow(false)
    val isIndicatorCollapsed: StateFlow<Boolean> = _isIndicatorCollapsed

    private val _titleCardStates = MutableStateFlow<Set<TitleCardStates>>(emptySet())
    val titleCardStates: StateFlow<Set<TitleCardStates>> = _titleCardStates.asStateFlow()

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

    fun scrollToItem(id: Int) {
        val id = (id - 2).coerceIn(0, Int.MAX_VALUE)
        _scrollEvents.trySend(TitlesScrollEvent.ScrollToItem(id))
    }

    fun onBanTheme(value: String) {
        viewModelScope.launch { repository.addBannedNew(value) }
    }

    fun isTitlesNumCorrect(): Boolean {
        return repository.getTitlesCount() == _titleCardStates.value.size
    }

    fun showGreeting(context: Context) {
        viewModelScope.launch {
            println("kljwda")
            val list = emptyList<Title>().toMutableList()
            list += Title(
                id = 0L,
                time = System.currentTimeMillis(),
                title = context.getString(R.string.greeting_1),
                text = "",
                sources = "",
                ids = ""
            )
            _titles.value = list.toList()
            delay(500L)
            list += Title(
                id = 1L,
                time = System.currentTimeMillis(),
                title = context.getString(R.string.greeting_2),
                text = "",
                sources = "",
                ids = ""
            )
            _titles.value = list.toList()
            delay(500L)
            list += Title(
                id = 2L,
                time = System.currentTimeMillis(),
                title = context.getString(R.string.greeting_3),
                text = "",
                sources = "",
                ids = ""
            )
            _titles.value = list.toList()
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
                SummarizationErrorType.FILTER_FAILED
            ) -> refreshTitles()
            SummarizationErrorType.JOB_CANCELLED -> requestNotificationPermission(activity)
            SummarizationErrorType.UNKNOWN_ERROR -> {
                val copiedText = "${err.cause}"
                clipboardManager.setText(AnnotatedString(copiedText))
            }
            else -> { }
        }
    }

    fun toggleTitleExpanded(id: Long?) {
        _titleCardStates.update { currentSet ->
            currentSet.map {
                when (id) {
                    null -> it.copy(expanded = false)
                    else -> {
                        if (it.id == id) {
                            it.copy(expanded = !it.expanded, read = true)
                        } else it
                    }
                }
            }.toSet()
        }
    }

    fun changeTitleCurrentPage(id: Long, newPage: Int) {
        _titleCardStates.update { currentSet ->
            currentSet.map {
                if (it.id == id) { it.copy(currentPage = newPage) }
                else it
            }.toSet()
        }
    }

    fun markTitleAsRead(id: Long, read: Boolean = true) {
        _titleCardStates.update { currentSet ->
            currentSet.map {
                if (it.id == id) { it.copy(read = read) }
                else it
            }.toSet()
        }
    }

    fun changeTitleSourceState(id: Long, source: String) {
        _titleCardStates.update { currentSet ->
            currentSet.map {
                if (it.id == id && it.sources != null) {
                    it.copy(
                        sources = (it.sources as Iterable<SourceMessages>).map { currentItem ->
                            SourceMessages(
                                source = currentItem.source,
                                state = if (currentItem.source == source) !currentItem.state else currentItem.state,
                                messages = currentItem.messages
                            )
                        }.toList()
                    )
                }
                else it
            }.toSet()
        }
    }

    fun clearErr() {
        repository.clearError()
    }

    fun getMessages(ids: String): List<Message>? = repository.getMessages(ids)

    fun changeGroupState(date: TimeDate) {
        val currentMap = _groupStates.value.toMutableMap()
        currentMap[date] = !(currentMap[date] ?: true)
        _groupStates.value = currentMap
    }

    fun lastTitlesUpdateExists(): Boolean {
        return repository.containsSetting(MewsRepository.LAST_TITLES_UPDATE)
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

    init {
        viewModelScope.launch {
            repository.titles
                .distinctUntilChanged()
                .collect { titleListFromDb ->
                    val actualTitles = titleListFromDb.filter { it.text != "<промежуточный текст>" }

                    _titles.value = actualTitles

                    _titleCardStates.update { currentStates ->
                        val oldStatesMap = currentStates.associateBy { it.id }
                        actualTitles.map { title ->
                            val oldState = oldStatesMap[title.id]
                            val messages = getMessages(title.ids)

                            val newSources = if (messages == null) null else {
                                val groupedMessages = messages.groupBy { it.source }
                                val sourceMessages = mutableListOf<SourceMessages>()
                                groupedMessages.forEach { (source, msgs) ->
                                    val oldSourceState = oldState?.sources
                                        ?.find { it.source == source }?.state ?: false

                                    sourceMessages.add(SourceMessages(source, oldSourceState, msgs))
                                }
                                sourceMessages.toList()
                            }

                            TitleCardStates(
                                id = title.id,
                                expanded = oldState?.expanded ?: false,
                                read = oldState?.read ?: false,
                                currentPage = oldState?.currentPage ?: 0,
                                sources = newSources
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

sealed interface TitlesScrollEvent {
    data object ScrollToTop : TitlesScrollEvent
    data class ScrollToItem(val id: Int) : TitlesScrollEvent
}

class TitlesViewModelFactory(private val application: Application) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TitlesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TitlesViewModel(application, MewsRepository) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}