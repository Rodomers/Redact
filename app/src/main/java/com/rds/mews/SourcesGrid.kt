package com.rds.mews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
fun SourcesGrid(itemsList: List<String>) {
    val buttons = remember {
        listOf(
            Pair("Кнопка") { println("Button 1") },
            Pair("Кнопка 2") { println("Button 2") },
            Pair("Кнопка 3") { println("Button 3") },
            Pair("Кнопка 4") { println("Button 4") },
            Pair("Кнопка 5") {println("Button 5")}
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = WindowInsets.statusBars.asPaddingValues(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(itemsList) { item ->
            CustomCardWithMenu(
                text = item,
                buttons
            )
        }
    }
}

@Preview
@Composable
fun AppScreen() {
    val myData = listOf("Элемент 1", "Элемент 2", "Элемент 3", "Элемент 4", "Элемент 5", "Элемент 6")
    SourcesGrid(itemsList = myData)
}