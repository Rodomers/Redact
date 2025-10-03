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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.collections.first
import kotlin.collections.last
import kotlin.text.toInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TitlesGrid(
    lazyGridState: LazyGridState,
    groupedItems: Map<String, List<Title>>,
    modifier: Modifier,
    isRefreshing: Boolean,
    showEmptyMess: Boolean,
    errState: SummarizationResult.Failure?,
//    expandedIds: Set<Long>,
    titlesCardStates: Set<TitleCardStates>,
    rememberCardPage: (Long, Int) -> Unit,
    onRefresh: () -> Unit,
    onClearErr: () -> Unit,
    onErrAction: (ClipboardManager) -> Unit,
    onToggleExpanded: (Long) -> Unit,
    showDates: Boolean,
    lastTitlesUpdate: Long,
    scope: CoroutineScope
) {
    val clipboardManager = LocalClipboardManager.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(errState) {
        if (errState != null) if (!bottomSheetState.isVisible) bottomSheetState.show()
        else if (bottomSheetState.isVisible) bottomSheetState.hide()
    }

    if (errState != null) {
        val resources = remember(errState) { mapResultToUiResources(errState) }

        CustomErrorBottomSheet(
            title = stringResource(resources[0]),
            text = stringResource(resources[1]),
            confBtnText = stringResource(resources[2]),
            cancelBtnText = stringResource(R.string.cancel),
            onDismissRequest = onClearErr,
            onConfirm = {
                scope.launch {
                    onErrAction(clipboardManager)
                    if (bottomSheetState.isVisible) bottomSheetState.hide()
                }.invokeOnCompletion { if (!bottomSheetState.isVisible) onClearErr() }
            },
            scope = scope,
            sheetState = bottomSheetState
        )
    }

    PullToRefreshBox(
        modifier = modifier
            .fillMaxSize(),
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            state = lazyGridState
        ) {
            if (showEmptyMess) {
                item {
                    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.titles_update_text),
                            fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(40.dp), textAlign = TextAlign.Center)
                    }
                }
            }

            groupedItems.forEach { (date, titlesForDate) ->
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
                    val isExpanded = titlesCardStates.find { it.id == item.id }?.expanded ?: false
                    val pagerState = rememberPagerState(initialPage = titlesCardStates.find { it.id == item.id }?.currentPage ?: 0, initialPageOffsetFraction = 0f, pageCount = {2})

                    TitlesCard(item, isExpanded = isExpanded, pagerState = pagerState, onToggleExpanded = { onToggleExpanded(item.id) }, rememberPage = { page -> rememberCardPage(item.id, page) })
                }
            }

            if (groupedItems.isNotEmpty()) {
                item { TitlesGridFootnote(lastTitlesUpdate) }
                item {Spacer(modifier = Modifier.height(1.dp))}
            }
        }
    }
}

@Composable
private fun TitlesGridFootnote(
    updatedMills: Long
) {
    var fPair by remember { mutableStateOf(0 to "") }
    var date by remember { mutableStateOf("") }
    fPair = formatUpdateTime(updatedMills)
    date = when (fPair.first) {
        0 -> {
            val formattedDate = getFormattedTimeUnix(date = true, unixTime = updatedMills).split(".")

            when (formattedDate.last().toInt()) {
                1 -> stringResource(R.string.date_01, formattedDate.first().toInt())
                2 -> stringResource(R.string.date_02, formattedDate.first().toInt())
                3 -> stringResource(R.string.date_03, formattedDate.first().toInt())
                4 -> stringResource(R.string.date_04, formattedDate.first().toInt())
                5 -> stringResource(R.string.date_05, formattedDate.first().toInt())
                6 -> stringResource(R.string.date_06, formattedDate.first().toInt())
                7 -> stringResource(R.string.date_07, formattedDate.first().toInt())
                8 -> stringResource(R.string.date_08, formattedDate.first().toInt())
                9 -> stringResource(R.string.date_09, formattedDate.first().toInt())
                10 -> stringResource(R.string.date_10, formattedDate.first().toInt())
                11 -> stringResource(R.string.date_11, formattedDate.first().toInt())
                12 -> stringResource(R.string.date_12, formattedDate.first().toInt())
                else -> fPair.second
            }
        }
        else -> stringResource(fPair.first)
    }

    CustomBottomFootnote(text = stringResource(R.string.updated_footnote, date, fPair.second))
}