package com.rds.mews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.launch

@Composable
fun SourcesGrid(
    itemsList: List<String>, modifier: Modifier, db: DbHelper,
    onSourcesChanged: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var delSourceName by remember { mutableStateOf("") }
    var changeDialog by remember { mutableStateOf("") }
    val addDialogTrue = { showAddDialog = true }

    val scope = rememberCoroutineScope()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        contentPadding = WindowInsets.statusBars.asPaddingValues(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items = itemsList, key = { item -> item }) { item ->
            CustomCardWithMenu(
                text = item,
                listOf(
                    Pair("Изменить") { changeDialog = item },
                    Pair("Удалить") { delSourceName = item }
                )
            )
        }

        item {
            SourcesAddCard(addDialogTrue)
        }

        if (showAddDialog) {
            item {
                Popup(
                    onDismissRequest = { showAddDialog = false },
                    alignment = Alignment.TopEnd
                ) {
                    CustomChangeDialog(
                        cancelAction = { showAddDialog = false },
                        onConfirm = {pair ->
                            scope.launch {
                                addSource(pair.first, pair.second, db)
                                onSourcesChanged()
                            }
                            showAddDialog = false
                        },
                        add = true,
                        scope = scope
                    )
                }
            }
        }
        if (delSourceName != "") {
            item {
                Popup(
                    onDismissRequest = { delSourceName = "" },
                    alignment = Alignment.TopEnd
                ) {
                    CustomConfirmDialog(
                        title = "Удаление источника",
                        text = "Вы уверены, что хотите удалить источник?",
                        cancelAction = { delSourceName = "" },
                        btnText = "Удалить",
                        onConfirm = {flag ->
                            scope.launch {
                                delSource(delSourceName, db)
                                onSourcesChanged()
                                delSourceName = ""
                            }
                        }
                    )
                }
            }
        }
        if (changeDialog != "") {
            item {
                Popup(
                    onDismissRequest = { delSourceName = "" },
                    alignment = Alignment.TopEnd
                ) {
                    CustomChangeDialog(
                        cancelAction = { changeDialog = "" },
                        onConfirm = {pair ->
                            scope.launch {
                                changeSource(pair.first, pair.second, db)
                                onSourcesChanged()
                                changeDialog = ""
                            }
                        },
                        add = false,
                        source = changeDialog
                    )
                }
            }
        }
    }
}