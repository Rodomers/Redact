package com.rds.mews.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rds.mews.localcore.SourcesGroupState
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.localcore.RSS
import com.rds.mews.localcore.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SourcesViewModel(private val repository: MewsRepository): ViewModel() {
    private val _scrollEvents = Channel<SourcesScrollEvent>(Channel.CONFLATED)
    val scrollEvents = _scrollEvents.receiveAsFlow()

    private val _rssLinkBuffer = MutableStateFlow("")
    val rssLinkBuffer: StateFlow<String> = _rssLinkBuffer

    private val _sourceNameBuffer = MutableStateFlow("")
    val sourceNameBuffer: StateFlow<String> = _sourceNameBuffer

    private val _isLinkCorrect = MutableStateFlow(false)
    val isLinkCorrect: StateFlow<Boolean> = _isLinkCorrect

    private val _expandedSourcesIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedSourcesIds: StateFlow<Set<Long>> = _expandedSourcesIds

    val sources: StateFlow<List<RSS>> = repository.getSourcesWithAvatars()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val groupedSources: StateFlow<Map<SourceType, List<RSS>>> = sources
        .map { list ->
            list.groupBy { it.sourceType }
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())


    val newSourcesPermitted: StateFlow<Boolean> = sources
        .map { list -> list.size < 40 }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _groupStates = MutableStateFlow<Map<SourceType, Boolean>>(emptyMap())
    val groupStates: StateFlow<List<SourcesGroupState>> = combine(
        groupedSources,
        _groupStates
    ) { sourceMap, groupMap ->
        sourceMap.map { (key, _) ->
            SourcesGroupState(key, groupMap[key] ?: true)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )


    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog

    private val _delSource = MutableStateFlow<RSS?>(null)
    val delSource: StateFlow<RSS?> = _delSource

    private val _changeSource = MutableStateFlow<RSS?>(null)
    val changedSource: StateFlow<RSS?> = _changeSource

    fun setShowAddDialog(value: Boolean) {
        _showAddDialog.value = value
    }

    fun setDelSource(value: RSS?) {
        _delSource.value = value
    }

    fun setCardExpanded(id: Long) {
        if (_expandedSourcesIds.value.contains(id)) _expandedSourcesIds.value -= id
        else _expandedSourcesIds.value += id
    }

    fun resetErrCount(sourceId: Long) {
        viewModelScope.launch { repository.resetErrorCount(sourceId) }
    }

    fun setChangeSource(value: RSS?) {
        _changeSource.value = value
    }

    fun addSource(context: Context, name: String, link: String) {
        viewModelScope.launch {
            repository.addSource(context, name, link)
        }
    }

    fun deleteSource(id: Long) {
        viewModelScope.launch {
            repository.deleteSource(id)
        }
    }

    fun changeSource(id: Long, newName: String) {
        viewModelScope.launch {
            repository.changeSource(id, newName)
        }
    }

    fun changeGroupState(group: SourceType) {
        val currentMap = _groupStates.value.toMutableMap()
        currentMap[group] = !(currentMap[group] ?: true)
        _groupStates.value = currentMap
    }

    private var resolveJob: Job? = null

    fun setRssLinkBuffer(value: String) {
        _rssLinkBuffer.value = value

        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            delay(400)

            val rssName = repository.getRssName(value)
            val linkName = if (value == "") null else rssName
            when (linkName) {
                null -> {
                    _sourceNameBuffer.value = ""
                    _isLinkCorrect.value = false
                }
                else -> {
                    _sourceNameBuffer.value = linkName
                    _isLinkCorrect.value = true
                }
            }
        }
    }

    fun setSourceNameBuffer(value: String) {
        _sourceNameBuffer.value = value
    }

    fun scrollToTop() {
        _scrollEvents.trySend(SourcesScrollEvent.ScrollToTop)
    }
}

sealed interface SourcesScrollEvent {
    data object ScrollToTop : SourcesScrollEvent
}

class SourcesViewModelFactory() : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SourcesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SourcesViewModel(MewsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}