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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SourcesGrid(itemsList: List<String>, modifier: Modifier) {
    val buttons = remember {
        listOf(
            Pair("Изменить") {println("Изменить")},
            Pair("Удалить") {println("Удалить")}
        )
    }

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
                buttons
            )
        }

        item {
            SourcesAddCard({ println("[eq") })
        }
    }
}

@Preview
@Composable
fun AppScreen() {
    val myData = listOf("Элемент 1", "Элемент 2", "Элемент 3", "Элемент 4", "Элемент 5", "Элемент 6", "Элемент 7", "Элемент 8", "Элемент 9", "Элемент 10", "Элемент 11", "Элемент 12",
        "Элемент 13", "Элемент 14", "Элемент 15", "Элемент 16", "Элемент 17", "Элемент 18","Элемент 19", "Элемент 20", "Элемент 21", "Элемент 22", "Элемент 23", "Элемент 24","Элемент 25", "Элемент 26", "Элемент 27", "Элемент 28", "Элемент 29", "Элемент 30","Элемент 31", "Элемент 32", "Элемент 33", "Элемент 34", "Элемент 35", "Элемент 36","Элемент 37", "Элемент 38", "Элемент 39", "Элемент 40", "Элемент 41", "Элемент 42",)
    SourcesGrid(itemsList = myData, modifier = Modifier)
}