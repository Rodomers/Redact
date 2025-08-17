package com.rds.mews

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rds.mews.ui.theme.MewsTheme


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MewsTheme {
                MainScreen()
                // TestBtn()
            }
        }
    }
}

sealed class TabScreen(val title: String, val icon: ImageVector) {
    data object Sources: TabScreen(title = "Источники", Icons.Default.Favorite)
    data object Titles: TabScreen(title = "Заголовки", Icons.Rounded.Menu)
    data object Settings: TabScreen(title = "Настройки", Icons.Default.Settings)
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf<TabScreen>(TabScreen.Sources) }

    val settingsManager = SettingsManager(LocalContext.current.applicationContext)
    val factory = SettingsViewModelFactory(settingsManager)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val db = DbHelper(LocalContext.current.applicationContext)

    val sourcesList: SnapshotStateList<String> = remember { mutableStateListOf() }
    val updateSources: () -> Unit = {
        sourcesList.clear()
        sourcesList.addAll(db.getRSS().map {it.source})
    }

    val titlesList: SnapshotStateList<Title> = remember { mutableStateListOf() }
    val fetcher = RssFetcher(db)
    val llm = OpenRouterClient()
    val summarizer = NewsSummarizer(db, llm)
    val scope = rememberCoroutineScope()
    var isTitlesRefreshing by remember { mutableStateOf(false) }

    fun refreshTitles(returnExisting: Boolean = false) {
        scope.launch {
            isTitlesRefreshing = true
            val updatedList = withContext(Dispatchers.IO) {
                // Вызываем нашу новую чистую функцию
                updateTitles(db, fetcher, summarizer, settingsViewModel, returnExisting = returnExisting)
            }
            // Полностью заменяем состояние. Это 100% вызовет рекомпозицию.
            titlesList.clear()
            titlesList.addAll(updatedList)
            isTitlesRefreshing = false
        }
    }

    Scaffold(
        bottomBar = {
            MyBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { newTab ->
                    selectedTab = newTab
                },
            )
        }
    ) { paddingValues ->
        when (selectedTab) {
            TabScreen.Sources -> {
                LaunchedEffect(key1 = Unit) {
                    updateSources()
                }

                SourcesGrid(
                    sourcesList,
                    modifier = Modifier.padding(paddingValues),
                    db = db,
                    onSourcesChanged = updateSources
                    )
            }
            TabScreen.Titles -> {
                LaunchedEffect(key1 = Unit) {
                    refreshTitles(returnExisting = true)
                }

                TitlesGrid(
                    itemsList = titlesList, // Передаем наш state list
                    modifier = Modifier.padding(paddingValues),
                    isRefreshing = isTitlesRefreshing,
                    onRefresh = ::refreshTitles // Передаем функцию для вызова по событию
                )
            }
            else -> SettingsGrid(
                modifier = Modifier.padding(paddingValues),
                settingsModel = settingsViewModel
            )
        }
    }
}

// --------------------ЧАСТЬ ДЛЯ ДЕБАГА--------------------------------
//@Composable
//fun TestBtn() {
//    val scope = rememberCoroutineScope()
//    var db = DbHelper(LocalContext.current.applicationContext)
//    db.getRSS().forEach { RSS ->  db.delRSS(RSS.id)}
//    db.getMessages().forEach { mess ->  db.delMessage(mess.id)}
//    db.getTitles().forEach { title ->  db.delTitle(title.id)}
//    db.addRSS("ТАСС", "https://tass.ru/rss/v2.xml")
//    db.addRSS("РИА", "https://ria.ru/export/rss2/index.xml")
//    var fetcher = RssFetcher(db)
//    var llm = OpenRouterClient()
//    var summarizer = NewsSummarizer(db, llm)
//    Box(
//        modifier = Modifier
//            .padding(horizontal = 20.dp, vertical = 100.dp)
//            .fillMaxWidth(),
//        contentAlignment = Alignment.Center
//    ) {
//        Button(
//            onClick = {
//                scope.launch(Dispatchers.IO) {
//                    try {
//                        println("start")
//                        if(fetcher.fetchAndStoreAll().errors.isEmpty()) {
//                            println("second")
//                            summarizer.summarizeTopics()
//                           db.getTitles().forEach { title ->
//                               println("${title.time}\t${title.title}\t${title.text}\t${title.sources}\t${title.links}")
//                           }
//                        }
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                    }
//                }
//                      },
//        ) {
//            Text(text = "Кнопка", fontWeight = FontWeight.Bold)
//        }
//    }
//}
//------------------------------------------------------------------------

@Composable
fun MyBottomBar(selectedTab: TabScreen, onTabSelected: (TabScreen) -> Unit) {
    val tabs = listOf(TabScreen.Sources, TabScreen.Titles, TabScreen.Settings)

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(imageVector = tab.icon, contentDescription = tab.title)
                },
                label = {
                    Text(text = tab.title)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    }
}