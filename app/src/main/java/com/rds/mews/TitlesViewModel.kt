package com.rds.mews

import android.app.Application
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TitlesViewModel(
    private val application: Application,
    private val repository: MewsRepository
): AndroidViewModel(application) {
    val gridState = LazyGridState(
        firstVisibleItemIndex = 0,
        firstVisibleItemScrollOffset = 0
    )

    private val workManager = WorkManager.getInstance(application)
    val workInfo: StateFlow<WorkInfo?> = workManager
        .getWorkInfosForUniqueWorkFlow("titles_update_work")
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _titles = MutableStateFlow<List<Title>>(emptyList())
    val titles = _titles.asStateFlow()

    val isRefreshing: StateFlow<Boolean> = combine(
        workInfo,
        repository.updatingTitles
    ) { info, isUpdating ->
        val isWorkerRunning = info?.state == WorkInfo.State.RUNNING

        isWorkerRunning && isUpdating
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val showDates: StateFlow<Boolean> = repository.showDates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val enlargedTimestamps: StateFlow<Boolean> = repository.enlargedTimestamps.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), false)
    val lastUpdated: StateFlow<Long> = repository.lastTitlesUpdate.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), 0)
    private val _errState = MutableStateFlow<SummarizationResult.Failure?>(null)
    val errState = _errState.asStateFlow()

    val groupedTitles: StateFlow<Map<String, List<Title>>> = titles
        .filter { it.isNotEmpty() }
        .map {list ->
        list.filter { it.text != "<промежуточный текст>" }.map {
            it.copy(sources = strTransform(it.sources, ", "),
                links = strTransform(it.links, "\n"))
        }.groupBy { getFormattedTimeUnix(it.time, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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

    @OptIn(ExperimentalMaterial3Api::class)
    fun handleErrorAction(clipboardManager: ClipboardManager) {
        val err = errState.value ?: return

        when (err.type) {
            in listOf(
                SummarizationErrorType.EXTRACT_TOPICS_FAILED,
                SummarizationErrorType.SUMMARIZE_TOPICS_FAILED,
                SummarizationErrorType.EMPTY_ANSWER,
                SummarizationErrorType.NETWORK_TIMEOUT,
                SummarizationErrorType.FILTER_FAILED
            ) -> refreshTitles()
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

    fun clearErr() {
        repository.clearError()
    }

    init {
        viewModelScope.launch {
            repository.titles.collect { titleListFromDb ->
                val actualTitles = titleListFromDb.filter { it.text != "<промежуточный текст>" }

                _titles.value = actualTitles

                if (_titleCardStates.value.map { it.id }.toSet() != actualTitles.map { it.id }.toSet()) {
                    _titleCardStates.value = actualTitles
                        .map { title -> TitleCardStates(id = title.id) }
                        .toSet()
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