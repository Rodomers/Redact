package com.rds.mews.viewmodels

import android.app.Application
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.rds.mews.MainActivity
import com.rds.mews.Message
import com.rds.mews.SourceMessages
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.SummarizationErrorType
import com.rds.mews.SummarizationResult
import com.rds.mews.Title
import com.rds.mews.TitleCardStates
import com.rds.mews.localcore.getFormattedTimeUnix
import com.rds.mews.localcore.requestNotificationPermission
import com.rds.mews.localcore.strTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private val _titles = MutableStateFlow<List<Title>>(emptyList())
    val titles = _titles.asStateFlow()

    val isRefreshing: StateFlow<Boolean> = repository.updatingTitles

    val showDates: StateFlow<Boolean> = repository.showDates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val enlargedTimestamps: StateFlow<Boolean> = repository.enlargedTimestamps.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), false)

    val lastUpdated: StateFlow<Long> = repository.lastTitlesUpdate
        .stateIn(viewModelScope,
            SharingStarted.WhileSubscribed(5000), 0)

    private val _errState = MutableStateFlow<SummarizationResult.Failure?>(null)
    val errState = _errState.asStateFlow()

    val groupedTitles: StateFlow<Map<String, List<Title>>> = titles
        .filter { it.isNotEmpty() }
        .distinctUntilChanged()
        .map { list ->
            list.filter { it.text != "<промежуточный текст>" }.map {
                it.copy(
                    sources = strTransform(it.sources, ", "),
                    ids = strTransform(it.ids, "\n")
                )
            }.groupBy { getFormattedTimeUnix(it.time, true) }
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _showEmptyMessage = MutableStateFlow(false)
    val showEmptyMess: StateFlow<Boolean> = _showEmptyMessage

    private val _titleCardStates = MutableStateFlow<Set<TitleCardStates>>(emptySet())
    val titleCardStates: StateFlow<Set<TitleCardStates>> = _titleCardStates.asStateFlow()

    fun toggleEmptyMess(newValue: Boolean) {
        _showEmptyMessage.value = newValue
    }

    fun refreshTitles() {
        repository.startTitlesUpdate(application)
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
                            it.copy(expanded = !it.expanded)
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

    init {
        viewModelScope.launch {
            repository.titles
                .distinctUntilChanged()
                .collect { titleListFromDb ->
                    val actualTitles = titleListFromDb.filter { it.text != "<промежуточный текст>" }

                    _titles.value = actualTitles

                    _titleCardStates.update { currentStates ->
                        val currentIds = currentStates.map { it.id }.toSet()
                        val newIds = actualTitles.map { it.id }.toSet()

                        if (currentIds != newIds) {
                            actualTitles
                                .map { title ->
                                    val messages = getMessages(title.ids)
                                    TitleCardStates(
                                        id = title.id,
                                        sources = if (messages == null) null else {
                                            val groupedMessages = messages.groupBy { it.source }
                                            var sourceMessages: MutableList<SourceMessages> = emptyList<SourceMessages>().toMutableList()
                                            groupedMessages.forEach { (source, messages) ->
                                                sourceMessages.add(SourceMessages(source, false, messages))
                                            }
                                            sourceMessages.toList()
                                        }
                                    )
                                }
                                .toSet()
                        } else {
                            currentStates
                        }
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