package com.rds.mews

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesGrid(
    gridState: LazyGridState,
    itemsList: List<RSS>,
    modifier: Modifier,
    onSourceAdd: (String, String) -> Unit,
    onSourceDelete: (String) -> Unit,
    onSourceChange: (String, String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var delSourceName by remember { mutableStateOf("") }
    var changeDialog by remember { mutableStateOf("") }
    val addDialogTrue = { showAddDialog = true }
    var newSourcesPermitted by remember { mutableStateOf(itemsList.size < 40) }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val scope = rememberCoroutineScope()

    val groupedBySource by remember(itemsList) {
        derivedStateOf {
            itemsList.groupBy { defineSourceType(it.link) }
        }
    }

    if (showAddDialog) {
        CustomChangeBottomSheet(
            onDismissRequest = { showAddDialog = false },
            onConfirm = {pair ->
                scope.launch {
                    onSourceAdd(pair.first, pair.second)
                    newSourcesPermitted = itemsList.size + 1 < 40
                }
                showAddDialog = false
                        },
            add = true,
            scope = scope,
            sheetState = bottomSheetState
        )
    }
    if (delSourceName != "") {
        CustomErrorBottomSheet(
            title = stringResource(R.string.delsource_title),
            text = stringResource(R.string.delsource_text, delSourceName),
            onDismissRequest = { delSourceName = "" },
            cancelBtnText = stringResource(R.string.cancel),
            confBtnText = stringResource(R.string.delsource_btntext),
            onConfirm = {
                scope.launch {
                    onSourceDelete(delSourceName)
                    delSourceName = ""
                }
            },
            scope = scope,
            sheetState = bottomSheetState
        )
    }
    if (changeDialog != "") {
        Popup(
            onDismissRequest = { delSourceName = "" },
            alignment = Alignment.TopEnd
        ) {
            CustomChangeBottomSheet(
                onDismissRequest = { changeDialog = "" },
                onConfirm = {pair ->
                    scope.launch {
                        onSourceChange(pair.first, pair.second)
                        changeDialog = ""
                    }
                },
                add = false,
                source = changeDialog,
                scope = scope,
                sheetState = bottomSheetState,
                sourceLink = itemsList.find { it.source == changeDialog }?.link
            )
        }
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
        if (groupedBySource.isEmpty()) {
            stickyHeader() {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomTextDivider(text = stringResource(R.string.no_sources))
                }
            }
        }

        groupedBySource.forEach { (source, itemsForSource) ->
            stickyHeader() {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomTextDivider(text = stringResource(sourcesTypeInterpreter(source)))
                }
            }

            items(items = itemsForSource, key = { item -> item.id }) { item ->
                CustomCardWithMenu(
                    text = item.source,
                    listOf(
                        Pair(stringResource(R.string.source_change)) { changeDialog = item.source },
                        Pair(stringResource(R.string.source_delete)) { delSourceName = item.source }
                    )
                )
            }

            if (itemsForSource.size % 2 != 0) item { Spacer(modifier = Modifier.height(1.dp)) }
        }


        if (newSourcesPermitted) {
            item { Spacer(modifier = Modifier.height(1.dp)) }
            item { Spacer(modifier = Modifier.height(1.dp)) }

            item {
                SourcesAddCard(addDialogTrue)
            }
        }
    }
}