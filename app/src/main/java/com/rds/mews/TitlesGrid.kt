package com.rds.mews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitlesGrid(itemsList: List<Title>,
               modifier: Modifier,
               isRefreshing: Boolean,
               onRefresh: () -> Unit,
               settingsViewModel: SettingsViewModel
) {
    val showDates by remember { mutableStateOf(settingsViewModel.showDates.value) }

    PullToRefreshBox(
        modifier = modifier.fillMaxSize(),
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
//        indicator = { CustomPullToRefreshIndicator() }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            contentPadding = WindowInsets.statusBars.asPaddingValues(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (itemsList.isEmpty()) {
                item {
                    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Потяните вниз, чтобы запустить загрузку\n(очень долгую)",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(40.dp), textAlign = TextAlign.Center)
                    }
                }
            }
            items(items = itemsList, key = { item -> item.id }) { item ->
                TitlesCard(item, showDates)
            }
        }
    }
}