package com.rds.mews.ui.grids

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.ui.Modifier
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
import com.rds.mews.localcore.sourcesTypeInterpreter
import com.rds.mews.ui.custom_elements.AddSourceBottomSheet
import com.rds.mews.ui.custom_elements.ExpandableContainer
import com.rds.mews.ui.custom_elements.SourcesCard
import com.rds.mews.ui.custom_elements.CustomErrorBottomSheet
import com.rds.mews.ui.custom_elements.EditSourceBottomSheet
import com.rds.mews.ui.custom_elements.SourcesAddCard
import com.rds.mews.ui.custom_elements.customHeader
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
        resetErrCount = viewModel::resetErrCount
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesGrid(
    gridState: LazyGridState,
    groupedItems: Map<SourceType, List<RSS>>,
    groupStates: List<SourcesGroupState>,
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
    resetErrCount: (Long) -> Unit
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
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
                onHeaderClick = { changeGroupState(source) }
            )

            items(
                items = itemsForSource,
                key = { it.id }
            ) { item ->
                ExpandableContainer(
                    visible = isExpanded
                ) {
                    Box(modifier = Modifier.padding(bottom = verticalArrangement * 2)) {
                        val buttons = listOf(
                            TextButtonInputs(stringResource(R.string.source_change), {
                                setChangeSource(
                                    item
                                )
                            }),
                            TextButtonInputs(
                                stringResource(R.string.source_delete),
                                { setDelSource(item) }),
                        )
                        val error = item.errCount >= 3
                        SourcesCard(
                            source = item.currentName ?: item.originalName,
                            buttons = if (!error) buttons else listOf(
                                TextButtonInputs(
                                    stringResource(R.string.source_reset_errors),
                                    { resetErrCount(item.id) }
                                )
                            ) + buttons,
                            error = error
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
}