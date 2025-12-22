package com.rds.mews.ui.grids

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.MainActivity
import com.rds.mews.R
import com.rds.mews.SummarizationResult
import com.rds.mews.Title
import com.rds.mews.TitleCardStates
import com.rds.mews.localcore.formatUpdateTime
import com.rds.mews.localcore.getFormattedTimeUnix
import com.rds.mews.localcore.mapResultToUiResources
import com.rds.mews.ui.custom_elements.CustomBottomFootnote
import com.rds.mews.ui.custom_elements.CustomErrorBottomSheet
import com.rds.mews.ui.custom_elements.CustomPullToRefreshIndicator
import com.rds.mews.ui.custom_elements.LegacyTextDivider
import com.rds.mews.ui.custom_elements.CustomTimeMark
import com.rds.mews.ui.custom_elements.TitlesCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.first
import kotlin.collections.iterator
import kotlin.collections.last
import kotlin.text.toInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TitlesGrid(
    lazyGridState: LazyGridState,
    mainActivity: MainActivity,
    groupedItems: Map<String, List<Title>>,
    modifier: Modifier,
    isRefreshing: Boolean,
    showEmptyMess: Boolean,
    toggleEmptyMess: (Boolean) -> Unit,
    errState: SummarizationResult.Failure?,
    titlesCardStates: Set<TitleCardStates>,
    rememberCardPage: (Long, Int) -> Unit,
    onRefresh: () -> Unit,
    onClearErr: () -> Unit,
    onErrAction: (ClipboardManager, MainActivity) -> Unit,
    onToggleExpanded: (Long) -> Unit,
    showDates: Boolean,
    lastTitlesUpdate: Long,
    scope: CoroutineScope,
    endureTime: Boolean = false,
    onBanTheme: (String) -> Unit,
    onConfigChange: (Int) -> Unit
) {
   val config = LocalConfiguration.current
    val clipboardManager = LocalClipboardManager.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(config) {
        val cardId = titlesCardStates.firstOrNull { it.expanded }?.id ?: -1
        if (cardId != -1L) {
            var globIndex = 0
            var item = 0
            for ((_, items) in groupedItems) {
                globIndex++
                val itIndex = items.indexOfFirst { it.id == cardId }
                if (itIndex != -1) item = itIndex + globIndex
                globIndex += items.size
            }
            onConfigChange(item)
        }
    }

    LaunchedEffect(groupedItems.isEmpty(), isRefreshing) {
        if (groupedItems.isEmpty() && !isRefreshing) {
            delay(300L)
            if (groupedItems.isEmpty()) toggleEmptyMess(true)
        }
        else toggleEmptyMess(false)
    }

    LaunchedEffect(errState) {
        if (errState != null) if (!bottomSheetState.isVisible) bottomSheetState.show()
        else if (bottomSheetState.isVisible) bottomSheetState.hide()
    }

    if (errState != null && !isRefreshing) {
        val resources = remember(errState) { mapResultToUiResources(errState) }

        CustomErrorBottomSheet(
            title = stringResource(resources[0]),
            text = stringResource(resources[1]),
            confBtnText = stringResource(resources[2]),
            cancelBtnText = stringResource(R.string.cancel),
            onDismissRequest = onClearErr,
            onConfirm = {
                scope.launch {
                    onErrAction(clipboardManager, mainActivity)
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
        indicator = {
            CustomPullToRefreshIndicator(
                state = pullToRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter),
                isRefreshing = isRefreshing
            )
        }
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
                        LegacyTextDivider(date = true, dateString = date)
                    }
                }

                items(items = titlesForDate, key = {it.id}) {item ->
                    val isExpanded = titlesCardStates.find { it.id == item.id }?.expanded ?: false
                    val pagerState = rememberPagerState(initialPage = titlesCardStates.find { it.id == item.id }?.currentPage ?: 0, initialPageOffsetFraction = 0f, pageCount = {2})

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 4000.dp),
                    ) {
                        if (endureTime) {
                            CustomTimeMark(item.time)
                        }
                        TitlesCard(
                            item,
                            isExpanded = isExpanded,
                            pagerState = pagerState,
                            onToggleExpanded = { onToggleExpanded(item.id) },
                            rememberPage = { page -> rememberCardPage(item.id, page) },
                            noTime = endureTime,
                            onBanTheme = onBanTheme
                        )
                    }
                }
            }

            if (groupedItems.isNotEmpty()) {
                item { TitlesGridFootnote(lastTitlesUpdate) }
                item { Spacer(modifier = Modifier.height(1.dp)) }
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