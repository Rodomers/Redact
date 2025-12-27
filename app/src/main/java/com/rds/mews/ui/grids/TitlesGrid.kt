package com.rds.mews.ui.grids

import TimelineMarker
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rds.mews.MainActivity
import com.rds.mews.R
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.localcore.TimeDate
import com.rds.mews.localcore.Title
import com.rds.mews.localcore.TitleCardStates
import com.rds.mews.localcore.TitlesGroupState
import com.rds.mews.localcore.mapResultToUiResources
import com.rds.mews.localcore.updatingStateInterpreter
import com.rds.mews.ui.custom_elements.ExpandableContainer
import com.rds.mews.ui.custom_elements.CustomBottomFootnote
import com.rds.mews.ui.custom_elements.CustomErrorBottomSheet
import com.rds.mews.ui.custom_elements.CustomPullToRefreshIndicator
import com.rds.mews.ui.custom_elements.TitlesCard
import com.rds.mews.ui.custom_elements.customHeader
import com.rds.mews.viewmodels.TitlesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale.getDefault
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import com.rds.mews.ui.theme.Shapes
import androidx.compose.ui.input.nestedscroll.NestedScrollSource.Companion.UserInput

@Composable
fun TitlesScreen(
    viewModel: TitlesViewModel,
    lazyGridState: LazyGridState,
    mainActivity: MainActivity,
    modifier: Modifier,
    scope: CoroutineScope,
    bottomSpacer: Dp
) {
    val groupedItems by viewModel.groupedTitles.collectAsStateWithLifecycle()
    val groupStates by viewModel.groupStates.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val updatingState by viewModel.updatingState.collectAsStateWithLifecycle()
    val updatingProgress by viewModel.updatingProgress.collectAsStateWithLifecycle()
    val isIndicatorCollapsed by viewModel.isIndicatorCollapsed.collectAsStateWithLifecycle()
    val err by viewModel.errState.collectAsStateWithLifecycle()
    val showEmptyMess by viewModel.showEmptyMess.collectAsStateWithLifecycle()
    val titlesCardStates by viewModel.titleCardStates.collectAsStateWithLifecycle()
    val innerTime by viewModel.innerTimestamps.collectAsStateWithLifecycle()
    val showSnippets by viewModel.showSnippets.collectAsStateWithLifecycle()
    val lastTitlesUpdate by viewModel.lastUpdated.collectAsStateWithLifecycle()

    TitlesGrid(
        lazyGridState = lazyGridState,
        mainActivity = mainActivity,
        groupedItems = groupedItems,
        groupStates = groupStates,
        modifier = modifier,
        isRefreshing = isRefreshing,
        updatingState = updatingState ?: "updating",
        updatingProgress = updatingProgress,
        indicatorCollapsed = isIndicatorCollapsed,
        showEmptyMess = showEmptyMess,
        toggleEmptyMess = viewModel::toggleEmptyMess,
        errState = err,
        titlesCardStates = titlesCardStates,
        rememberCardPage = viewModel::changeTitleCurrentPage,
        onRefresh = viewModel::refreshTitles,
        onIndicatorClick = viewModel::changeIndicatorCollapsed,
        onClearErr = viewModel::clearErr,
        onErrAction = viewModel::handleErrorAction,
        onToggleExpanded = viewModel::toggleTitleExpanded,
        lastTitlesUpdate = lastTitlesUpdate,
        scope = scope,
        innerTime = innerTime,
        showSnippets = showSnippets,
        bottomSpacer = bottomSpacer,
        onBanTheme = viewModel::onBanTheme,
        onConfigChange = viewModel::scrollToItem,
        changeSourceState = viewModel::changeTitleSourceState,
        changeGroupState = viewModel::changeGroupState,
        getDateFromUnix = viewModel::getDateFromUnix,
        showGreeting = viewModel::showGreeting,
        lastTitlesUpdateExists = viewModel::lastTitlesUpdateExists,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TitlesGrid(
    lazyGridState: LazyGridState,
    mainActivity: MainActivity,
    groupedItems: Map<TimeDate, List<Title>>,
    groupStates: List<TitlesGroupState>,
    modifier: Modifier,
    isRefreshing: Boolean,
    updatingState: String,
    updatingProgress: Float,
    indicatorCollapsed: Boolean,
    showEmptyMess: Boolean,
    toggleEmptyMess: (Boolean) -> Unit,
    errState: SummarizationResult.Failure?,
    titlesCardStates: Set<TitleCardStates>,
    rememberCardPage: (Long, Int) -> Unit,
    onRefresh: () -> Unit,
    onIndicatorClick: () -> Unit,
    onClearErr: () -> Unit,
    onErrAction: (ClipboardManager, MainActivity) -> Unit,
    onToggleExpanded: (Long) -> Unit,
    lastTitlesUpdate: Long,
    scope: CoroutineScope,
    innerTime: Boolean,
    showSnippets: Boolean,
    bottomSpacer: Dp,
    onBanTheme: (String) -> Unit,
    onConfigChange: (Int) -> Unit,
    changeSourceState: (Long, String) -> Unit,
    changeGroupState: (TimeDate) -> Unit,
    getDateFromUnix: (Long) -> TimeDate,
    showGreeting: (Context) -> Unit,
    lastTitlesUpdateExists: () -> Boolean
) {
    val verticalArrangement by remember { mutableStateOf(8.dp) }

    val config = LocalConfiguration.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pullToRefreshState = rememberPullToRefreshState()
    val lastUpdatedDate = remember(groupedItems) { mutableStateOf(getDateFromUnix(lastTitlesUpdate)) }

    var allowPullToRefresh by remember { mutableStateOf(false) }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == UserInput) {
                    if (available.y > 0 && !allowPullToRefresh) {
                        return available
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(groupedItems.isEmpty()) {
        if (!lastTitlesUpdateExists() && groupedItems.isEmpty()) showGreeting(context)
    }

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
        if (groupedItems.isEmpty() && !isRefreshing && lastTitlesUpdateExists()) {
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
                isRefreshing = isRefreshing,
                statusText = context.getString(updatingStateInterpreter(updatingState)),
                progress = updatingProgress,
                isCollapsed = indicatorCollapsed,
                onCollapseChange = { onIndicatorClick() },
            )
        }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier
                .nestedScroll(connection)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitFirstDown(requireUnconsumed = false)
                            allowPullToRefresh = !lazyGridState.canScrollBackward
                        }
                    }
                }
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            contentPadding = WindowInsets.statusBars.asPaddingValues(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            state = lazyGridState
        ) {
            if (showEmptyMess) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .wrapContentSize()
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = Shapes.large
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.zhdun),
                                contentDescription = "zhdun.swag",
                                modifier = Modifier
                                    .size(256.dp)
                                    .padding(top = 50.dp),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer)
                            )
                            Text(
                                text = stringResource(R.string.titles_update_text),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(40.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            var globalIndex = 0

            groupedItems.forEach { (date, titlesForDate) ->
                customHeader(
                    text = if (date.number != null) context.getString(
                        date.date,
                        date.number
                    ) else context.getString(date.date)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() },
                    isExpanded = groupStates.find { it.group == date }?.expanded ?: true,
                    onHeaderClick = { changeGroupState(date) }
                )
                globalIndex++
                val currentGroupStartIndex = globalIndex

                itemsIndexed(titlesForDate) { index, item ->
                    val isFirst = index == 0
                    val isLast = index == titlesForDate.lastIndex

                    val itemGlobalIndex = currentGroupStartIndex + index

                    val statesItem = titlesCardStates.find { it.id == item.id }
                    val isExpanded = statesItem?.expanded ?: false
                    val pagerState = rememberPagerState(initialPage = statesItem?.currentPage ?: 0, initialPageOffsetFraction = 0f, pageCount = {2})
                    val sources = statesItem?.sources
                    val read = statesItem?.read ?: false

                    val isPartiallyObscured by remember {
                        derivedStateOf {
                            val firstIndex = lazyGridState.firstVisibleItemIndex
                            val firstOffset = lazyGridState.firstVisibleItemScrollOffset
                            itemGlobalIndex == firstIndex && firstOffset > 0
                        }
                    }

                    ExpandableContainer(
                        visible = groupStates.find { it.group == date }?.expanded ?: true
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                        ) {
                            if (!innerTime) {
                                TimelineMarker(
                                    time = item.time,
                                    isFirst = isFirst,
                                    isLast = isLast
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                TitlesCard(
                                    item,
                                    modifier = Modifier.padding(vertical = verticalArrangement),
                                    isExpanded = isExpanded,
                                    pagerState = pagerState,
                                    onToggleExpanded = { onToggleExpanded(item.id) },
                                    rememberPage = { page -> rememberCardPage(item.id, page) },
                                    noTime = !innerTime,
                                    showSnippet = showSnippets,
                                    onBanTheme = onBanTheme,
                                    sources = sources,
                                    changeSourceState = changeSourceState,
                                    backgroundColor = if (read)
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha=0.5f)
                                    else MaterialTheme.colorScheme.secondaryContainer,
                                    expandable = lastTitlesUpdateExists()
                                )

                                if (isPartiallyObscured) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {  }
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                globalIndex += titlesForDate.size
            }

            if (groupedItems.isNotEmpty() && lastTitlesUpdateExists()) {
                item {
                    CustomBottomFootnote(
                        text = stringResource(
                            R.string.updated_footnote,
                            if (lastUpdatedDate.value.number == null) stringResource(lastUpdatedDate.value.date)
                            else stringResource(lastUpdatedDate.value.date, lastUpdatedDate.value.number!!),
                            lastUpdatedDate.value.time
                        ),
                        modifier = Modifier.padding(vertical = verticalArrangement)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(bottomSpacer))
            }
        }
    }
}