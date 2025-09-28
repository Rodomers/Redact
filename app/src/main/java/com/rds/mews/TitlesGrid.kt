package com.rds.mews

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TitlesGrid(itemsList: List<Title>,
               modifier: Modifier,
               isRefreshing: Boolean,
               onRefresh: () -> Unit,
               settingsViewModel: SettingsViewModel,
               closeIndicator: () -> Unit,
               scope: CoroutineScope
) {
    val clipboardManager = LocalClipboardManager.current

    val titlesExpanded = remember { mutableStateMapOf<Long, Boolean>() }

    val pullToRefreshState = rememberPullToRefreshState()

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val errState by settingsViewModel.lastError.collectAsStateWithLifecycle()

    val showDates by settingsViewModel.showDates

    val groupedByDate = remember(itemsList) {
        itemsList.groupBy { getFormattedTimeUnix(it.time, true) }
    }

    LaunchedEffect(itemsList) {
        val currentIds = itemsList.map {it.id}.toSet()
        titlesExpanded.keys.retainAll(currentIds)
    }

    LaunchedEffect(errState) {
        when (errState) {
            null -> {
                bottomSheetState.show()
                closeIndicator()
            }
            else -> if (bottomSheetState.isVisible) bottomSheetState.hide()
        }
    }

    if (errState != null) {
        val err = errState!!
        val resources = remember(err) { mapResultToUiResources(err) }

        CustomErrorBottomSheet(
            title = stringResource(resources[0]),
            text = stringResource(resources[1]),
            confBtnText = stringResource(resources[2]),
            cancelBtnText = stringResource(R.string.cancel),
            onDismissRequest = {
                settingsViewModel.clearError()
                settingsViewModel.setUpdatingState("off")
                               },
            onConfirm = {
                scope.launch {
                    when (err.type) {
                        in listOf(SummarizationErrorType.EXTRACT_TOPICS_FAILED,
                            SummarizationErrorType.SUMMARIZE_TOPICS_FAILED,
                            SummarizationErrorType.NETWORK_TIMEOUT,
                            SummarizationErrorType.FILTER_FAILED) -> {
                            onRefresh()
                        }
                        SummarizationErrorType.UNKNOWN_ERROR -> {
                            val copiedText = "${errState?.cause ?: "errCode is null"}"
                            clipboardManager.setText(AnnotatedString(copiedText))
                        }
                        else -> {  }
                    }

                    if (bottomSheetState.isVisible) bottomSheetState.hide()
                }.invokeOnCompletion { if (!bottomSheetState.isVisible) settingsViewModel.clearError() }
            },
            scope = scope,
            sheetState = bottomSheetState
        )
    }

    PullToRefreshBox(
        modifier = modifier.fillMaxSize(),
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicator = { CustomPullToRefreshIndicator(
            state = pullToRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter),
            isRefreshing = isRefreshing
        ) }
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
            if (itemsList.isEmpty() && !isRefreshing) {
                item {
                    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.titles_update_text),
                            fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(40.dp), textAlign = TextAlign.Center)
                    }
                }
            }

            groupedByDate.forEach { (date, titlesForDate) ->
                if (showDates) stickyHeader() {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CustomTextDivider(date = true, dateString = date)
                    }
                }

                items(items = titlesForDate, key = {it.id}) {item ->
                    val isExpanded = titlesExpanded[item.id] ?: false
                    val pagerState = rememberPagerState(initialPage = 0, initialPageOffsetFraction = 0f, pageCount = {2})

                    TitlesCard(item, isExpanded = isExpanded, pagerState = pagerState, onToggleExpanded = {
                        titlesExpanded[item.id] = !isExpanded
                    })
                }
            }
        }
    }
}