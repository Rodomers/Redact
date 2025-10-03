package com.rds.mews

import android.app.Application
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
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

    private val _titles = MutableStateFlow<List<Title>>(emptyList())
    val titles = _titles.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val showDates: StateFlow<Boolean> = repository.showDates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val lastUpdated: StateFlow<Long> = repository.lastTitlesUpdate.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), 0)
    val errState: StateFlow<SummarizationResult.Failure?> = repository.lastError.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val groupedTitles: StateFlow<Map<String, List<Title>>> = titles.map {list ->
        list.groupBy { getFormattedTimeUnix(it.time, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _showEmptyMessage = MutableStateFlow(false)
    val showEmptyMess: StateFlow<Boolean> = _showEmptyMessage

    private val _titleCardStates = MutableStateFlow<Set<TitleCardStates>>(emptySet())
    val titleCardStates: StateFlow<Set<TitleCardStates>> = _titleCardStates.asStateFlow()

    init {
        viewModelScope.launch {
            repository.titles.collect { titleList ->
                _titles.value = titleList

                _titleCardStates.value = titleList
                    .map {title -> TitleCardStates(id = title.id) }
                    .toSet()
            }
        }
    }

    fun refreshTitles(returnExisting: Boolean = false) {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch

            _isRefreshing.value = true
            try {
                val freshTitles = repository.fetchNewTitles(application, returnExisting)
                 _titles.value = freshTitles.filter { it.text != "<промежуточный текст>" }
                _titleCardStates.value = _titles.value
                    .map {title -> TitleCardStates(id = title.id) }
                    .toSet()
            } catch (e: Exception) {
                repository.saveLastError(SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e.cause))
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun handleErrorAction(clipboardManager: ClipboardManager) {
        val err = errState.value ?: return

        when (err.type) {
            in listOf(
                SummarizationErrorType.EXTRACT_TOPICS_FAILED,
                SummarizationErrorType.SUMMARIZE_TOPICS_FAILED,
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

    fun toggleTitleExpanded(id: Long) {
        _titleCardStates.update { currentSet ->
            currentSet.map {
                if (it.id == id) { it.copy(expanded = !it.expanded) }
                else it
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