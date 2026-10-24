package com.rds.mews.ui.grids

import com.rds.mews.ui.custom_elements.bottom_sheet.CustomErrorBottomSheet
import com.rds.mews.ui.custom_elements.TimelineMarker
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.rds.mews.ui.custom_elements.ExpandableContainer
import com.rds.mews.ui.custom_elements.CustomBottomFootnote
import com.rds.mews.ui.custom_elements.CustomPullToRefreshIndicator
import com.rds.mews.ui.custom_elements.titles_card.TitlesCard
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
import androidx.compose.ui.res.pluralStringResource
import com.rds.mews.localcore.MediaWithSource
import com.rds.mews.localcore.SourceMessages
import com.rds.mews.localcore.TitleSorting
import com.rds.mews.localcore.UpdatingState
import com.rds.mews.settings_manager.SummarizationErrorType
import com.rds.mews.ui.custom_elements.image_viewer.RootViewOverlay
import com.rds.mews.ui.custom_elements.titles_card.BlitzCardSourceExpansionOverlay
import com.rds.mews.ui.custom_elements.titles_card.BlitzCarouselRow
import com.rds.mews.viewmodels.TitlesScrollEvent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Duration.Companion.milliseconds

private data class ExpandedBlitzData(
    val title: Title,
    val bounds: Rect?,
    val dateString: String,
    val sources: List<SourceMessages>? = null
)

@Composable
fun TitlesScreen(
    viewModel: TitlesViewModel,
    lazyGridState: LazyGridState,
    mainActivity: MainActivity,
    modifier: Modifier,
    scope: CoroutineScope,
    bottomSpacer: Dp,
    onOverscrollDetected: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        viewModel.scrollEvents.collect { event ->
            when (event) {
                is TitlesScrollEvent.ScrollToTop -> lazyGridState.animateScrollToItem(0)
                is TitlesScrollEvent.ScrollToItem -> {
                    if (event.animated) {
                        val targetIndex = event.id
                        val currentIndex = lazyGridState.firstVisibleItemIndex
                        val distance = kotlin.math.abs(targetIndex - currentIndex)

                        if (distance > 10) {
                            val preTargetIndex = if (targetIndex > currentIndex) {
                                targetIndex - 5
                            } else {
                                targetIndex + 5
                            }
                            lazyGridState.scrollToItem(preTargetIndex)
                        }

                        lazyGridState.animateScrollToItem(
                            index = targetIndex,
                            scrollOffset = -150
                        )
                    } else {
                        lazyGridState.scrollToItem(event.id)
                    }
                }
            }
        }
    }

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
    val dynamicMediaUrls by viewModel.dynamicMediaUrls.collectAsStateWithLifecycle()
    val titleSorting by viewModel.titleSorting.collectAsStateWithLifecycle()
    val copyPlainText by viewModel.copyPlainText.collectAsStateWithLifecycle()
    val failedTitles by viewModel.failedTitles.collectAsStateWithLifecycle()
    val blitzCarouselPositions by viewModel.blitzCarouselPositions.collectAsStateWithLifecycle()
    val blitzImagePages by viewModel.blitzImagePages.collectAsStateWithLifecycle()

    TitlesGrid(
        lazyGridState = lazyGridState,
        mainActivity = mainActivity,
        groupedItems = groupedItems,
        groupStates = groupStates,
        modifier = modifier,
        isRefreshing = isRefreshing,
        updatingState = updatingState,
        updatingProgress = updatingProgress,
        indicatorCollapsed = isIndicatorCollapsed,
        showEmptyMess = showEmptyMess,
        toggleEmptyMess = viewModel::toggleEmptyMess,
        errState = err,
        titlesCardStates = titlesCardStates,
        blitzCarouselPositions = blitzCarouselPositions,
        blitzImagePages = blitzImagePages,
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
        copyPlainText = copyPlainText,
        bottomSpacer = bottomSpacer,
        titleSorting = titleSorting,
        dynamicMediaUrls = dynamicMediaUrls,
        failedTitles = failedTitles,
        onBanTheme = viewModel::onBanTheme,
        scrollToItem = viewModel::scrollToItem,
        changeSourceState = viewModel::changeTitleSourceState,
        changeGroupState = viewModel::changeGroupState,
        getDateFromUnix = viewModel::getDateFromUnix,
        markTitleAsRead = viewModel::markTitleAsRead,
        markTitleAsPinned = viewModel::markTitleAsPinned,
        showGreeting = viewModel::showGreeting,
        lastTitlesUpdateExists = viewModel::lastTitlesUpdateExists,
        onSwitchStoryline = viewModel::switchStorylineAndScroll,
        loadDynamicMediaUrls = viewModel::loadDynamicMediaUrls,
        setCurrentTitleImage = viewModel::setCurrentTitleImage,
        onSaveBlitzScroll = viewModel::saveBlitzCarouselScroll,
        setFullscreenView = viewModel::setFullscreenImageForTitle,
        stopTitlesUpdate = viewModel::stopTitlesUpdate,
        setShowMedia = viewModel::setShowMedia,
        onOverscrollDetected = onOverscrollDetected
    )
}

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TitlesGrid(
    lazyGridState: LazyGridState,
    mainActivity: MainActivity,
    groupedItems: Map<TimeDate, List<Title>>,
    groupStates: List<TitlesGroupState>,
    modifier: Modifier,
    isRefreshing: Boolean,
    updatingState: UpdatingState,
    updatingProgress: Float,
    indicatorCollapsed: Boolean,
    showEmptyMess: Boolean,
    toggleEmptyMess: (Boolean) -> Unit,
    errState: SummarizationResult.Failure?,
    titlesCardStates: Set<TitleCardStates>,
    blitzCarouselPositions:  Map<String, Pair<Int, Int>>,
    blitzImagePages:  Map<Long, Int>,
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
    copyPlainText: Boolean,
    titleSorting: TitleSorting,
    failedTitles: Int,
    bottomSpacer: Dp,
    dynamicMediaUrls: Map<Long, List<MediaWithSource>>,
    onBanTheme: (String) -> Unit,
    scrollToItem: (Int) -> Unit,
    changeSourceState: (Long, String) -> Unit,
    changeGroupState: (TimeDate) -> Unit,
    getDateFromUnix: (Long) -> TimeDate,
    markTitleAsRead: (Long, Boolean) -> Unit,
    markTitleAsPinned: (Long, Boolean) -> Unit,
    showGreeting: (Context) -> Unit,
    lastTitlesUpdateExists: () -> Boolean,
    onSwitchStoryline: (Long) -> Unit,
    loadDynamicMediaUrls: (Long) -> Unit,
    setCurrentTitleImage: (Long, Int) -> Unit,
    onSaveBlitzScroll: (String, Int, Int) -> Unit,
    setFullscreenView: (Long, Boolean) -> Unit,
    stopTitlesUpdate: (Context) -> Unit,
    setShowMedia: (Long, Boolean) -> Unit,
    onOverscrollDetected: () -> Unit = {}
) {
    val verticalArrangement by remember { mutableStateOf(8.dp) }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pullToRefreshState = rememberPullToRefreshState()
    val lastUpdatedDate = remember(groupedItems) { mutableStateOf(getDateFromUnix(lastTitlesUpdate)) }
    val expandedCardId = remember(titlesCardStates) {
        titlesCardStates.firstOrNull { it.expanded }?.id ?: -1L
    }

    var allowPullToRefresh by remember { mutableStateOf(false) }
    var lastOverscrollTime by remember { mutableLongStateOf(0L) }
    var expandedBlitzData by remember { mutableStateOf<ExpandedBlitzData?>(null) }

    val connection = remember(onOverscrollDetected) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == UserInput) {
                    if (kotlin.math.abs(available.y) > 0.5f) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastOverscrollTime > 1500L) {
                            lastOverscrollTime = currentTime
                            onOverscrollDetected()
                        }
                    }

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

    LaunchedEffect(groupedItems.keys) {
        if (expandedCardId != -1L && groupedItems.isNotEmpty()) {
            var globIndex = 0
            var item = -1
            var foundGroup: TimeDate? = null

            for ((group, items) in groupedItems) {
                globIndex++
                val regularItems = items.filter { !it.isBlitz }
                val hasBlitz = items.any { it.isBlitz }
                val itIndex = regularItems.indexOfFirst { it.id == expandedCardId }
                if (itIndex != -1) {
                    item = itIndex + globIndex
                    foundGroup = group
                    break
                }
                globIndex += regularItems.size + if (hasBlitz) 1 else 0
            }

            if (item != -1) {
                if (foundGroup != null && groupStates.any { it.group == foundGroup && !it.expanded }) changeGroupState(foundGroup)
                scrollToItem(item)
            }
        }
    }

    LaunchedEffect(groupedItems.isEmpty(), isRefreshing) {
        if (groupedItems.isEmpty() && !isRefreshing && lastTitlesUpdateExists()) {
            delay(300L.milliseconds)
            if (groupedItems.isEmpty()) toggleEmptyMess(true)
        } else toggleEmptyMess(false)
    }

    LaunchedEffect(errState) {
        if (errState != null) if (!bottomSheetState.isVisible) bottomSheetState.show()
        else if (bottomSheetState.isVisible) bottomSheetState.hide()
    }

    if (errState != null && !isRefreshing) {
        val resources = remember(errState) { mapResultToUiResources(errState) }

        CustomErrorBottomSheet(
            title = stringResource(resources[0]),
            text = if (errState.type != SummarizationErrorType.UNPROCESSED_ITEMS) stringResource(resources[1])
            else pluralStringResource(resources[1], failedTitles, failedTitles),
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
        modifier = modifier.fillMaxSize(),
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicator = {
            CustomPullToRefreshIndicator(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
                statusText = context.getString(updatingState.stringId),
                progress = updatingProgress,
                isCollapsed = indicatorCollapsed,
                onCollapseChange = { onIndicatorClick() },
                onCancellation = { stopTitlesUpdate(context) }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                            modifier = Modifier.fillMaxWidth(),
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
                                    modifier = Modifier.padding(40.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                var globalIndex = 0

                groupedItems.forEach { (date, allTitlesForDate) ->
                    val regularTitles = allTitlesForDate.filter { !it.isBlitz }
                    val blitzTitles = allTitlesForDate.filter { it.isBlitz }
                    val isGroupExpanded = groupStates.find { it.group == date }?.expanded ?: true

                    customHeader(
                        text = if (date.number != null) context.getString(
                            date.date,
                            date.number
                        ) else context.getString(date.date)
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() },
                        isExpanded = isGroupExpanded,
                        onHeaderClick = { changeGroupState(date) },
                        expandable = lastTitlesUpdateExists(),
                        bottomPadding = 0.dp
                    )
                    globalIndex++
                    val currentGroupStartIndex = globalIndex

                    itemsIndexed(
                        items = regularTitles,
                        key = { _, item -> item.id }
                    ) { index, item ->
                        val isFirst = index == 0
                        val isLast = index == regularTitles.lastIndex

                    val itemGlobalIndex = currentGroupStartIndex + index

                    val statesItem = titlesCardStates.find { it.id == item.id }
                    val isExpanded = statesItem?.expanded ?: false
                    val pagerState = rememberPagerState(
                        initialPage = statesItem?.currentPage ?: 0,
                        initialPageOffsetFraction = 0f,
                        pageCount = { 2 }
                    )
                    val sources = statesItem?.sources

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
                                    time = item.eventTime,
                                    isFirst = isFirst,
                                    isLast = isLast,
                                    isRead = item.isRead,
                                    isPinned = item.isPinned
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                val imagePagerState = rememberPagerState(
                                    initialPage = statesItem?.currentImage ?: 0,
                                    pageCount = { dynamicMediaUrls[item.id]?.size ?: 0 }
                                )
                                val scrollState = rememberScrollState()
                                val clickedImageIndex = if (statesItem?.fullscreenImage == true) statesItem.currentImage else null

                                TitlesCard(
                                    title = item,
                                    modifier = Modifier.padding(vertical = verticalArrangement),
                                    isExpanded = isExpanded,
                                    pagerState = pagerState,
                                    onToggleExpanded = {
                                        onToggleExpanded(item.id)
                                        markTitleAsRead(item.id, true)
                                    },
                                    rememberPage = { page -> rememberCardPage(item.id, page) },
                                    noTime = !innerTime,
                                    showSnippet = showSnippets,
                                    onBanTheme = onBanTheme,
                                    sources = sources,
                                    changeSourceState = changeSourceState,
                                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                    expandable = lastTitlesUpdateExists(),
                                    markAsUnread = {
                                        onToggleExpanded(item.id)
                                        markTitleAsRead(item.id, false)
                                    },
                                    markAsPinned = { isPinned ->
                                        markTitleAsPinned(item.id, isPinned)
                                    },
                                    onSwitchStoryline = onSwitchStoryline,
                                    onLoadMediaUrls = { loadDynamicMediaUrls(item.id) },
                                    dynamicMediaUrls = dynamicMediaUrls[item.id],
                                    onImageChanged = { setCurrentTitleImage(item.id, it) },
                                    titleSorting = titleSorting,
                                    sanitizeCopiedText = copyPlainText,
                                    imagePagerState = imagePagerState,
                                    scrollState = scrollState,
                                    clickedImageIndex = clickedImageIndex,
                                    onImageClicked = { flag ->
                                        setFullscreenView(item.id, flag)
                                    },
                                    setShowMedia = setShowMedia
                                )

                                if (isPartiallyObscured) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { }
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                    globalIndex += regularTitles.size

                    if (blitzTitles.isNotEmpty()) {
                        val carouselKey = "blitz_carousel_${date.date}_${date.number}_${date.time}"

                        item(key = carouselKey) {
                            ExpandableContainer(visible = isGroupExpanded) {
                                val savedPos = blitzCarouselPositions[carouselKey]
                                val carouselState = rememberSaveable(
                                    carouselKey,
                                    saver = LazyListState.Saver
                                ) {
                                    LazyListState(
                                        firstVisibleItemIndex = savedPos?.first ?: 0,
                                        firstVisibleItemScrollOffset = savedPos?.second ?: 0
                                    )
                                }

                                LaunchedEffect(carouselState) {
                                    snapshotFlow { carouselState.firstVisibleItemIndex to carouselState.firstVisibleItemScrollOffset }
                                        .distinctUntilChanged()
                                        .collect { (idx, offset) ->
                                            onSaveBlitzScroll(carouselKey, idx, offset)
                                        }
                                }

                                BlitzCarouselRow(
                                    items = blitzTitles,
                                    dateStringProvider = { blitzItem ->
                                        val evDate = getDateFromUnix(blitzItem.eventTime)
                                        try {
                                            if (evDate.number != null) context.getString(evDate.date, evDate.number)
                                            else context.getString(evDate.date)
                                        } catch (_: Exception) { "" }
                                    },
                                    expandedTitleId = expandedBlitzData?.title?.id,
                                    titlesCardStates = titlesCardStates,
                                    dynamicMediaUrls = dynamicMediaUrls,
                                    carouselState = carouselState,
                                    cardImagePages = blitzImagePages,
                                    onToggleExpanded = { clickedTitle, bounds, dateStr ->
                                        expandedBlitzData = ExpandedBlitzData(
                                            title = clickedTitle,
                                            bounds = bounds,
                                            dateString = dateStr
                                        )
                                    },
                                    onTogglePin = { id, isPinned -> markTitleAsPinned(id, isPinned) },
                                    onLoadMediaUrls = { id -> loadDynamicMediaUrls(id) },
                                    onImageChanged = { id, page -> setCurrentTitleImage(id, page) },
                                    onImageClicked = { id, flag -> setFullscreenView(id, flag) },
                                    setShowMedia = setShowMedia,
                                    markTitleAsRead = markTitleAsRead,
                                    sanitizeCopiedText = copyPlainText
                                )
                            }
                        }
                        globalIndex++
                    }
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

            if (expandedBlitzData != null && expandedBlitzData!!.bounds != null) {
                val titleId = expandedBlitzData!!.title.id
                val blitzTitle = groupedItems.values.flatten().find { it.id == titleId } ?: expandedBlitzData!!.title
                val statesItem = titlesCardStates.find { it.id == titleId }
                val mediaList = dynamicMediaUrls[titleId]?.filter { (it.message?.source?.showMedia ?: false) && it.mediaLink.isNotBlank() } ?: emptyList()

                val currentImg = blitzImagePages[titleId] ?: statesItem?.currentImage ?: 0
                val overlayImagePagerState = rememberPagerState(
                    initialPage = currentImg,
                    pageCount = { mediaList.size }
                )
                val overlayClickedImageIndex = if (statesItem?.fullscreenImage == true) currentImg else null

                RootViewOverlay {
                    BlitzCardSourceExpansionOverlay(
                        title = blitzTitle,
                        dateString = expandedBlitzData!!.dateString,
                        sources = statesItem?.sources,
                        collapsedBounds = expandedBlitzData!!.bounds!!,
                        onDismissRequest = {
                            scope.launch {
                                delay(250.milliseconds)
                                expandedBlitzData = null
                            }
                        },
                        onTogglePin = { isPinned -> markTitleAsPinned(titleId, isPinned) },
                        dynamicMediaUrls = dynamicMediaUrls[titleId],
                        imagePagerState = overlayImagePagerState,
                        clickedImageIndex = overlayClickedImageIndex,
                        onImageChanged = { setCurrentTitleImage(titleId, it) },
                        onImageClicked = { setFullscreenView(titleId, it) },
                        setShowMedia = setShowMedia,
                        onBanTheme = onBanTheme
                    )
                }
            }
        }
    }
}