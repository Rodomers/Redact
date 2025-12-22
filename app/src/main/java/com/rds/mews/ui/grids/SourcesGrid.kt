package com.rds.mews.ui.grids

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rds.mews.R
import com.rds.mews.RSS
import com.rds.mews.SourceType
import com.rds.mews.localcore.sourcesTypeInterpreter
import com.rds.mews.ui.custom_elements.SourcesCard
import com.rds.mews.ui.custom_elements.CustomChangeBottomSheet
import com.rds.mews.ui.custom_elements.CustomErrorBottomSheet
import com.rds.mews.ui.custom_elements.LegacyTextDivider
import com.rds.mews.ui.custom_elements.SourcesAddCard
import com.rds.mews.viewmodels.SourcesViewModel
import kotlinx.coroutines.launch


@Composable
fun SourcesScreen(
    context: Context,
    gridState: LazyGridState,
    modifier: Modifier,
    viewModel: SourcesViewModel
) {
    val groupedTitles by viewModel.groupedSources.collectAsStateWithLifecycle()
    val newSourcesPermitted by viewModel.newSourcesPermitted.collectAsStateWithLifecycle()
    val delSource by viewModel.delSource.collectAsStateWithLifecycle()
    val changedSource by viewModel.changedSource.collectAsStateWithLifecycle()
    val showAddDialog by viewModel.showAddDialog.collectAsStateWithLifecycle()

    val onAddSource = remember(viewModel, context) {
        { name: String, link: String -> viewModel.addSource(context, name, link) }
    }

    SourcesGrid(
        gridState = gridState,
        groupedItems = groupedTitles,
        modifier = modifier,
        onSourceAdd = onAddSource,
        onSourceDelete = viewModel::deleteSource,
        onSourceChange = viewModel::changeSource,
        setShowAddDialog = viewModel::setShowAddDialog,
        setDelSource = viewModel::setDelSource,
        setChangeSource = viewModel::setChangeSource,
        newSourcesPermitted = newSourcesPermitted,
        deletedSource = delSource,
        changedSource = changedSource,
        showAddDialog = showAddDialog,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesGrid(
    gridState: LazyGridState,
    groupedItems: Map<SourceType, List<RSS>>,
    modifier: Modifier,
    onSourceAdd: (String, String) -> Unit,
    onSourceDelete: (Long) -> Unit,
    onSourceChange: (Long, String) -> Unit,
    setShowAddDialog: (Boolean) -> Unit,
    setDelSource: (RSS?) -> Unit,
    setChangeSource: (RSS?) -> Unit,
    newSourcesPermitted: Boolean,
    deletedSource: RSS?,
    changedSource: RSS?,
    showAddDialog: Boolean
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    if (showAddDialog) {
        CustomChangeBottomSheet(
            onDismissRequest = { setShowAddDialog(false) },
            onConfirm = { pair ->
                scope.launch {
                    onSourceAdd(pair.first, pair.second)
                }
                setShowAddDialog(false)
            },
            add = true,
            scope = scope,
            sheetState = bottomSheetState
        )
    }
    if (deletedSource != null) {
        CustomErrorBottomSheet(
            title = stringResource(R.string.delsource_title),
            text = stringResource(R.string.delsource_text, deletedSource.source),
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
        CustomChangeBottomSheet(
            onDismissRequest = { setChangeSource(null) },
            onConfirm = { pair ->
                scope.launch {
                    onSourceChange(changedSource.id, pair.second)
                    setChangeSource(null)
                }
            },
            add = false,
            source = changedSource.source,
            scope = scope,
            sheetState = bottomSheetState,
            sourceLink = changedSource.link
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        contentPadding = WindowInsets.statusBars.asPaddingValues(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        state = gridState
    ) {
        if (groupedItems.isEmpty()) {
            stickyHeader() {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LegacyTextDivider(text = stringResource(R.string.no_sources))
                }
            }
        }

        groupedItems.forEach { (source, itemsForSource) ->
            stickyHeader() {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LegacyTextDivider(text = stringResource(sourcesTypeInterpreter(source)))
                }
            }

            items(items = itemsForSource, key = { item -> item.id }) { item ->
                SourcesCard(
                    source = item.source,
                    listOf(
                        Pair(stringResource(R.string.source_change)) { setChangeSource(item) },
                        Pair(stringResource(R.string.source_delete)) { setDelSource(item) }
                    )
                )
            }

            if (itemsForSource.size % 2 != 0) item { Spacer(modifier = Modifier.height(1.dp)) }
        }


        if (newSourcesPermitted) {
            item { Spacer(modifier = Modifier.height(1.dp)) }
            item { Spacer(modifier = Modifier.height(1.dp)) }

            item {
                SourcesAddCard({ setShowAddDialog(true) }, transitionState = showAddDialog)
            }
        }
    }
}