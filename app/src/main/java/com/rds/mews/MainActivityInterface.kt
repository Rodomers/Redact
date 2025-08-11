package com.rds.mews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

sealed class TabScreen(val title: String, val icon: ImageVector) {
    data object Sources: TabScreen(title = "Источники", Icons.Default.Favorite)
    data object Titles: TabScreen(title = "Заголовки", Icons.Rounded.Menu)
    data object Settings: TabScreen(title = "Настройки", Icons.Default.Settings)
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf<TabScreen>(TabScreen.Sources) }

    Scaffold(
        bottomBar = {
            MyBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { newTab ->
                    selectedTab = newTab
                }
            )
        }
    ) { paddingValues ->
        when (selectedTab) {
            TabScreen.Sources -> SourcesGrid(
                listOf("Элемент 1", "Элемент 2", "Элемент 3", "Элемент 4", "Элемент 5", "Элемент 6")
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Выбран экран: ${selectedTab.title}")
                }
            }
        }
    }
}

@Composable
fun MyBottomBar(selectedTab: TabScreen, onTabSelected: (TabScreen) -> Unit) {
    val tabs = listOf(TabScreen.Sources, TabScreen.Titles, TabScreen.Settings)

    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(imageVector = tab.icon, contentDescription = tab.title)
                },
                label = {
                    Text(text = tab.title)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MainScreen()
}