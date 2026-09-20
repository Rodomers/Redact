package com.rds.mews.ui.grids

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rds.mews.localcore.SourcesGroupState
import com.rds.mews.R
import com.rds.mews.localcore.RSS
import com.rds.mews.localcore.SourceType
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.localcore.getFormattedTimeUnix
import com.rds.mews.localcore.sourcesTypeInterpreter
import com.rds.mews.ui.custom_elements.AddSourceBottomSheet
import com.rds.mews.ui.custom_elements.ExpandableContainer
import com.rds.mews.ui.custom_elements.SourcesCard
import com.rds.mews.ui.custom_elements.CustomErrorBottomSheet
import com.rds.mews.ui.custom_elements.EditSourceBottomSheet
import com.rds.mews.ui.custom_elements.SourcesAddCard
import com.rds.mews.ui.custom_elements.SourcesCardExpansionOverlay
import com.rds.mews.ui.custom_elements.customHeader
import com.rds.mews.ui.custom_elements.titles_card.RootViewOverlay
import com.rds.mews.viewmodels.SourcesViewModel
import kotlinx.coroutines.launch


@Composable
fun SourcesScreen(
    context: Context,
    gridState: LazyGridState,
    modifier: Modifier,
    viewModel: SourcesViewModel,
    bottomSpacer: Dp
) {
    val groupedTitles by viewModel.groupedSources.collectAsStateWithLifecycle()
    val groupStates by viewModel.groupStates.collectAsStateWithLifecycle()
    val expandedCards by viewModel.expandedSourcesIds.collectAsStateWithLifecycle()
    val newSourcesPermitted by viewModel.newSourcesPermitted.collectAsStateWithLifecycle()
    val delSource by viewModel.delSource.collectAsStateWithLifecycle()
    val changedSource by viewModel.changedSource.collectAsStateWithLifecycle()
    val showAddDialog by viewModel.showAddDialog.collectAsStateWithLifecycle()

    val sourceNameBuffer by viewModel.sourceNameBuffer.collectAsStateWithLifecycle()
    val rssLinkBuffer by viewModel.rssLinkBuffer.collectAsStateWithLifecycle()
    val isCorrectLink by viewModel.isLinkCorrect.collectAsStateWithLifecycle()

    val onAddSource = remember(viewModel, context) {
        { name: String, link: String -> viewModel.addSource(context, name, link) }
    }

    SourcesGrid(
        gridState = gridState,
        groupedItems = groupedTitles,
        groupStates = groupStates,
        expandedCards = expandedCards,
        modifier = modifier,
        onSourceAdd = onAddSource,
        onSourceDelete = viewModel::deleteSource,
        onSourceChange = viewModel::changeSource,
        setShowAddDialog = viewModel::setShowAddDialog,
        setDelSource = viewModel::setDelSource,
        setChangeSource = viewModel::setChangeSource,
        changeGroupState = viewModel::changeGroupState,
        newSourcesPermitted = newSourcesPermitted,
        deletedSource = delSource,
        changedSource = changedSource,
        showAddDialog = showAddDialog,
        sourceNameBuffer = sourceNameBuffer,
        rssLinkBuffer = rssLinkBuffer,
        isCorrectLink = isCorrectLink,
        bottomSpacer = bottomSpacer,
        setSourceNameBuffer = viewModel::setSourceNameBuffer,
        setRssLinkBuffer = viewModel::setRssLinkBuffer,
        onCardExpanded = viewModel::setCardExpanded,
        resetErrCount = viewModel::resetErrCount,
        setInBurst = viewModel::setInBurst,
        setShowMedia = viewModel::setShowMedia
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesGrid(
    gridState: LazyGridState,
    groupedItems: Map<SourceType, List<RSS>>,
    groupStates: List<SourcesGroupState>,
    expandedCards: Set<Long>,
    modifier: Modifier,
    onSourceAdd: (String, String) -> Unit,
    onSourceDelete: (Long) -> Unit,
    onSourceChange: (Long, String) -> Unit,
    setShowAddDialog: (Boolean) -> Unit,
    setDelSource: (RSS?) -> Unit,
    setChangeSource: (RSS?) -> Unit,
    changeGroupState: (SourceType) -> Unit,
    newSourcesPermitted: Boolean,
    deletedSource: RSS?,
    changedSource: RSS?,
    showAddDialog: Boolean,
    sourceNameBuffer: String,
    rssLinkBuffer: String,
    isCorrectLink: Boolean,
    bottomSpacer: Dp,
    setSourceNameBuffer: (String) -> Unit,
    setRssLinkBuffer: (String) -> Unit,
    onCardExpanded: (Long) -> Unit,
    resetErrCount: (Long) -> Unit,
    setInBurst: (Long, Boolean) -> Unit,
    setShowMedia: (Long, Boolean) -> Unit
) {
    val handler = LocalUriHandler.current

    val verticalArrangement by remember { mutableStateOf(8.dp) }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(changedSource) {
        if (sourceNameBuffer == "") setSourceNameBuffer(changedSource?.currentName ?: "")
    }

    if (showAddDialog) {
        AddSourceBottomSheet(
            rssLinkValue = rssLinkBuffer,
            onRssLinkChange = setRssLinkBuffer,
            sourceNameValue = sourceNameBuffer,
            onSourceNameChange = setSourceNameBuffer,
            isRssValid = isCorrectLink,
            onConfirm = { onSourceAdd(sourceNameBuffer, rssLinkBuffer) },
            onDismissRequest = {
                setShowAddDialog(false)
                setSourceNameBuffer("")
                setRssLinkBuffer("")
            },
            sheetState = bottomSheetState,
            scope = scope
        )
    }
    if (deletedSource != null) {
        CustomErrorBottomSheet(
            title = stringResource(R.string.delsource_title),
            text = stringResource(R.string.delsource_text, deletedSource.currentName ?: deletedSource.originalName),
            onDismissRequest = { setDelSource(null) },
            cancelBtnText = stringResource(R.string.cancel),
            confBtnText = stringResource(R.string.delsource_btntext),
            onConfirm = {
                scope.launch {
                    onSourceDelete(deletedSource.id)
                    setDelSource(null)
                }
            },
            scope = scope,
            sheetState = bottomSheetState
        )
    }
    if (changedSource != null) {
        EditSourceBottomSheet(
            sourceNameValue = sourceNameBuffer,
            onSourceNameChange = setSourceNameBuffer,
            originalSourceName = changedSource.originalName,
            onConfirm = { scope.launch {
                onSourceChange(changedSource.id, sourceNameBuffer.ifBlank { changedSource.originalName })
            } },
            onLinkClick = {
                try {
                    handler.openUri(changedSource.websiteUrl)
                } catch (_: Exception) {}
            },
            onDismissRequest = {
                setChangeSource(null)
                setSourceNameBuffer("")
            },
            sheetState = bottomSheetState,
            scope = scope
        )
    }

    var expandedBounds by remember { mutableStateOf<Rect?>(null) }
    var selectedRssId by remember { mutableStateOf<Long?>(null) }

    val selectedRss = remember(groupedItems, selectedRssId) {
        if (selectedRssId == null) null
        else groupedItems.values.flatten().find { it.id == selectedRssId }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .then(
                    if (selectedRssId != null && expandedBounds != null) {
                        Modifier.blur(radius = 16.dp)
                    } else Modifier
                ),
            contentPadding = WindowInsets.statusBars.asPaddingValues(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            state = gridState
        ) {
            if (groupedItems.isEmpty()) {
                customHeader(
                    textId = R.string.no_sources,
                    expandable = false
                )
            }

            groupedItems.toSortedMap().forEach { (source, itemsForSource) ->
                val isExpanded = groupStates.find { it.group == source }?.expanded ?: false

                customHeader(
                    textId = sourcesTypeInterpreter(source),
                    isExpanded = isExpanded,
                    onHeaderClick = { changeGroupState(source) },
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                items(
                    items = itemsForSource,
                    key = { it.id }
                ) { item ->
                    ExpandableContainer(
                        visible = isExpanded
                    ) {
                        Box(modifier = Modifier.padding(bottom = verticalArrangement * 2)) {
                            SourcesCard(
                                rss = item,
                                avatarUrl = item.avatarUrl,
                                timeText = when (item.lastUpdated) {
                                    in listOf(0L, null) -> "-"
                                    else -> getFormattedTimeUnix(item.lastUpdated ?: 0L)
                                },
                                isExpanded = selectedRssId == item.id,
                                onClick = { bounds ->
                                    expandedBounds = bounds
                                    selectedRssId = item.id
                                }
                            )
                        }
                    }
                }
            }

            if (newSourcesPermitted) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    SourcesAddCard({ setShowAddDialog(true) }, transitionState = showAddDialog)
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(bottomSpacer + verticalArrangement))
            }
        }

        if (selectedRss != null && expandedBounds != null) {
            val buttons = listOf(
                TextButtonInputs(stringResource(R.string.source_change), {
                    setChangeSource(selectedRss)
                }),
                TextButtonInputs(stringResource(R.string.source_delete), {
                    setDelSource(selectedRss)
                })
            )

            RootViewOverlay {
                SourcesCardExpansionOverlay(
                    rss = selectedRss,
                    buttons = buttons,
                    avatarUrl = selectedRss.avatarUrl,
                    timeText = when (selectedRss.lastUpdated) {
                        in listOf(0L, null) -> "-"
                        else -> getFormattedTimeUnix(selectedRss.lastUpdated ?: 0L)
                    },
                    collapsedBounds = expandedBounds!!,
                    onDismissRequest = {
                        selectedRssId = null
                        expandedBounds = null
                    },
                    onResetErrors = resetErrCount,
                    setInBurst = { inBurst -> setInBurst(selectedRss.id, inBurst) },
                    setShowMedia = { showMedia -> setShowMedia(selectedRss.id, showMedia) }
                )
            }
        }
    }
}