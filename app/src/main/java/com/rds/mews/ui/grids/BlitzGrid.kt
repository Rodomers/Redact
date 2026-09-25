package com.rds.mews.ui.grids

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rds.mews.MainActivity
import com.rds.mews.R
import com.rds.mews.core.text.TextSanitizer
import com.rds.mews.localcore.MediaWithSource
import com.rds.mews.localcore.SourceMessages
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.localcore.TimeDate
import com.rds.mews.localcore.Title
import com.rds.mews.localcore.TitleCardStates
import com.rds.mews.localcore.UpdatingState
import com.rds.mews.localcore.mapResultToUiResources
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.settings_manager.SummarizationErrorType
import com.rds.mews.ui.custom_elements.CustomBottomFootnote
import com.rds.mews.ui.custom_elements.CustomErrorBottomSheet
import com.rds.mews.ui.custom_elements.CustomPullToRefreshIndicator
import com.rds.mews.ui.custom_elements.titles_card.BlitzCard
import com.rds.mews.ui.custom_elements.titles_card.BlitzCardSourceExpansionOverlay
import com.rds.mews.ui.custom_elements.image_viewer.RootViewOverlay
import com.rds.mews.ui.theme.Shapes
import com.rds.mews.viewmodels.BlitzViewModel
import com.rds.mews.viewmodels.TitleUiItem
import com.rds.mews.viewmodels.TitlesScrollEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

private data class ExpandedCardData(
    val title: Title,
    val bounds: Rect,
    val dateString: String,
    val sources: List<SourceMessages>? = null
)

@Composable
fun BlitzScreen(
    viewModel: BlitzViewModel,
    lazyStaggeredGridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    mainActivity: MainActivity,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    scope: CoroutineScope = rememberCoroutineScope(),
    bottomSpacer: Dp = 0.dp
) {
    LaunchedEffect(Unit) {
        viewModel.scrollEvents.collect { event ->
            when (event) {
                is TitlesScrollEvent.ScrollToTop -> {
                    if (lazyStaggeredGridState.firstVisibleItemIndex > 15) {
                        lazyStaggeredGridState.scrollToItem(0)
                    } else {
                        lazyStaggeredGridState.animateScrollToItem(0)
                    }
                }
                is TitlesScrollEvent.ScrollToItem -> {
                    if (event.animated) {
                        val targetIndex = event.id
                        val currentIndex = lazyStaggeredGridState.firstVisibleItemIndex
                        val distance = abs(targetIndex - currentIndex)

                        if (distance > 10) {
                            val preTargetIndex = if (targetIndex > currentIndex) {
                                targetIndex - 5
                            } else {
                                targetIndex + 5
                            }
                            lazyStaggeredGridState.scrollToItem(preTargetIndex)
                        }

                        lazyStaggeredGridState.animateScrollToItem(
                            index = targetIndex,
                            scrollOffset = -150
                        )
                    } else {
                        lazyStaggeredGridState.scrollToItem(event.id)
                    }
                }
            }
        }
    }

    val groupedItems by viewModel.groupedTitles.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val updatingState by viewModel.updatingState.collectAsStateWithLifecycle()
    val updatingProgress by viewModel.updatingProgress.collectAsStateWithLifecycle()
    val isIndicatorCollapsed by viewModel.isIndicatorCollapsed.collectAsStateWithLifecycle()
    val err by viewModel.errState.collectAsStateWithLifecycle()
    val showEmptyMess by viewModel.showEmptyMess.collectAsStateWithLifecycle()
    val lastTitlesUpdate by viewModel.lastUpdated.collectAsStateWithLifecycle()
    val dynamicMediaUrls by viewModel.dynamicMediaUrls.collectAsStateWithLifecycle()
    val titlesCardStates by viewModel.titleCardStates.collectAsStateWithLifecycle()
    val sanitizeCopiedText by viewModel.sanitizeCopiedText.collectAsStateWithLifecycle()
    val failedTitles by viewModel.failedTitles.collectAsStateWithLifecycle()

    BlitzGrid(
        lazyStaggeredGridState = lazyStaggeredGridState,
        mainActivity = mainActivity,
        groupedItems = groupedItems,
        modifier = modifier,
        isRefreshing = isRefreshing,
        updatingState = updatingState,
        updatingProgress = updatingProgress,
        indicatorCollapsed = isIndicatorCollapsed,
        showEmptyMess = showEmptyMess,
        sanitizeCopiedText = sanitizeCopiedText,
        toggleEmptyMess = viewModel::toggleEmptyMess,
        errState = err,
        titlesCardStates = titlesCardStates,
        onRefresh = viewModel::refreshTitles,
        onIndicatorClick = viewModel::changeIndicatorCollapsed,
        onClearErr = viewModel::clearErr,
        onErrAction = viewModel::handleErrorAction,
        lastTitlesUpdate = lastTitlesUpdate,
        failedTitles = failedTitles,
        scope = scope,
        bottomSpacer = bottomSpacer,
        dynamicMediaUrls = dynamicMediaUrls,
        getDateFromUnix = viewModel::getDateFromUnix,
        markTitleAsPinned = viewModel::markTitleAsPinned,
        markTitleAsRead = viewModel::markTitleAsRead,
        showGreeting = viewModel::showGreeting,
        lastTitlesUpdateExists = viewModel::lastTitlesUpdateExists,
        stopTitlesUpdate = viewModel::stopTitlesUpdate,
        onLoadMediaUrls = viewModel::loadDynamicMediaUrls,
        setCurrentTitleImage = viewModel::setCurrentTitleImage,
        setFullscreenView = viewModel::setFullscreenImageForTitle,
        setShowMedia = viewModel::setShowMedia
    )
}

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BlitzGrid(
    lazyStaggeredGridState: LazyStaggeredGridState,
    mainActivity: MainActivity,
    groupedItems: Map<Long?, List<TitleUiItem>>,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean,
    updatingState: UpdatingState,
    updatingProgress: Float,
    indicatorCollapsed: Boolean,
    showEmptyMess: Boolean,
    sanitizeCopiedText: Boolean,
    toggleEmptyMess: (Boolean) -> Unit,
    errState: SummarizationResult.Failure?,
    titlesCardStates: Set<TitleCardStates>,
    onRefresh: () -> Unit,
    onIndicatorClick: () -> Unit,
    onClearErr: () -> Unit,
    onErrAction: (ClipboardManager, MainActivity) -> Unit,
    lastTitlesUpdate: Long,
    failedTitles: Int,
    scope: CoroutineScope,
    bottomSpacer: Dp = 0.dp,
    dynamicMediaUrls: Map<Long, List<MediaWithSource>>,
    getDateFromUnix: (Long) -> TimeDate,
    markTitleAsPinned: (Long, Boolean) -> Unit,
    markTitleAsRead: (Long, Boolean) -> Unit,
    showGreeting: (Context) -> Unit,
    lastTitlesUpdateExists: () -> Boolean,
    stopTitlesUpdate: (Context) -> Unit,
    onLoadMediaUrls: (Long, Boolean) -> Unit,
    setCurrentTitleImage: (Long, Int) -> Unit,
    setFullscreenView: (Long, Boolean) -> Unit,
    setShowMedia: (Long, Boolean) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pullToRefreshState = rememberPullToRefreshState()
    val lastUpdatedDate = remember(groupedItems) { mutableStateOf(getDateFromUnix(lastTitlesUpdate)) }
    val haptics = LocalHapticFeedback.current

    var allowPullToRefresh by remember { mutableStateOf(false) }
    var expandedCardData by remember { mutableStateOf<ExpandedCardData?>(null) }

    LaunchedEffect(lazyStaggeredGridState) {
        snapshotFlow { lazyStaggeredGridState.layoutInfo.visibleItemsInfo }
            .map { visibleItems -> visibleItems.mapNotNull { it.key as? Long } }
            .distinctUntilChanged()
            .collect { visibleIds ->
                visibleIds.forEach { id -> markTitleAsRead(id, true) }
            }
    }

    LaunchedEffect(groupedItems.isEmpty()) {
        if (!lastTitlesUpdateExists() && groupedItems.isEmpty()) showGreeting(context)
    }

    LaunchedEffect(groupedItems.isEmpty(), isRefreshing) {
        if (groupedItems.isEmpty() && !isRefreshing && lastTitlesUpdateExists()) {
            delay(300L.milliseconds)
            if (groupedItems.isEmpty()) toggleEmptyMess(true)
        } else toggleEmptyMess(false)
    }

    LaunchedEffect(errState) {
        if (errState != null) {
            if (!bottomSheetState.isVisible) bottomSheetState.show()
        } else if (bottomSheetState.isVisible) {
            bottomSheetState.hide()
        }
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
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown(requireUnconsumed = false)
                                allowPullToRefresh = !lazyStaggeredGridState.canScrollBackward
                            }
                        }
                    }
                    .fillMaxSize()
                    .padding(horizontal = 10.dp)
                    .then(
                        if (expandedCardData != null) {
                            Modifier.blur(radius = 16.dp)
                        } else Modifier
                    ),
                contentPadding = WindowInsets.statusBars.asPaddingValues(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
                state = lazyStaggeredGridState
            ) {
                if (showEmptyMess) {
                    item(span = StaggeredGridItemSpan.FullLine) {
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

                groupedItems.entries.forEachIndexed { index, (updateTime, groupItems) ->
                    if (index > 0) {
                        item(
                            key = "spacer_$updateTime",
                            span = StaggeredGridItemSpan.FullLine
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    items(
                        items = groupItems,
                        key = { item -> item.title.id }
                    ) { item ->
                        val title = item.title

                        val dateString = try {
                            if (item.eventDate.number != null) context.getString(item.eventDate.date, item.eventDate.number)
                            else context.getString(item.eventDate.date)
                        } catch (_: Exception) { "" }

                        val isCardExpanded = expandedCardData?.title?.id == title.id
                        val statesItem = titlesCardStates.find { it.id == title.id }
                        val sources = statesItem?.sources

                        val validMediaCount = dynamicMediaUrls[title.id]?.count { it.mediaLink.isNotBlank() } ?: 0
                        val imagePagerState = rememberPagerState(
                            initialPage = statesItem?.currentImage ?: 0,
                            pageCount = { validMediaCount }
                        )
                        val clickedImageIndex = if (statesItem?.fullscreenImage == true) statesItem.currentImage else null

                        val summary = if (sanitizeCopiedText) TextSanitizer.sanitize(title.summary, saveWhitespace = true) else title.summary
                        val copiedText =
                            "${summary}\n\n${MewsRepository.getStringResource(R.string.titles_card_source)}: ${MewsRepository.getStringResource(R.string.app_name)}, ${title.sources}"

                        BlitzCard(
                            title = title,
                            dateString = dateString,
                            isExpanded = isCardExpanded,
                            onClick = { bounds ->
                                expandedCardData = ExpandedCardData(
                                    title = title,
                                    bounds = bounds,
                                    dateString = dateString,
                                    sources = sources
                                )
                            },
                            onTogglePin = { isPinned -> markTitleAsPinned(title.id, isPinned) },
                            onShare = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, copiedText)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            },
                            onLongClick = {
                                clipboardManager.setText(AnnotatedString(copiedText))
                                Toast.makeText(context, R.string.titles_card_copied, Toast.LENGTH_SHORT).show()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            dynamicMediaUrls = dynamicMediaUrls[title.id],
                            onLoadMediaUrls = { onLoadMediaUrls(title.id, false) },
                            imagePagerState = imagePagerState,
                            clickedImageIndex = clickedImageIndex,
                            onImageChanged = { setCurrentTitleImage(title.id, it) },
                            onImageClicked = { setFullscreenView(title.id, it) },
                            setShowMedia = setShowMedia,
                            modifier = Modifier
                        )
                    }
                }

                if (groupedItems.isNotEmpty() && lastTitlesUpdateExists()) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        CustomBottomFootnote(
                            text = stringResource(
                                R.string.updated_footnote,
                                if (lastUpdatedDate.value.number == null) stringResource(
                                    lastUpdatedDate.value.date
                                )
                                else stringResource(
                                    lastUpdatedDate.value.date,
                                    lastUpdatedDate.value.number!!
                                ),
                                lastUpdatedDate.value.time
                            ),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                item(span = StaggeredGridItemSpan.FullLine) {
                    Spacer(modifier = Modifier.height(bottomSpacer))
                }
            }

            if (expandedCardData != null) {
                val titleId = expandedCardData!!.title.id
                val statesItem = titlesCardStates.find { it.id == titleId }
                val validMediaCount = dynamicMediaUrls[titleId]?.count { it.mediaLink.isNotBlank() } ?: 0
                val overlayImagePagerState = rememberPagerState(
                    initialPage = statesItem?.currentImage ?: 0,
                    pageCount = { validMediaCount }
                )
                val overlayClickedImageIndex = if (statesItem?.fullscreenImage == true) statesItem.currentImage else null

                RootViewOverlay {
                    BlitzCardSourceExpansionOverlay(
                        title = expandedCardData!!.title,
                        dateString = expandedCardData!!.dateString,
                        sources = expandedCardData!!.sources,
                        collapsedBounds = expandedCardData!!.bounds,
                        onDismissRequest = {
                            expandedCardData = null
                        },
                        dynamicMediaUrls = dynamicMediaUrls[titleId],
                        imagePagerState = overlayImagePagerState,
                        clickedImageIndex = overlayClickedImageIndex,
                        onImageChanged = { setCurrentTitleImage(titleId, it) },
                        setShowMedia = setShowMedia,
                        onImageClicked = { setFullscreenView(titleId, it) }
                    )
                }
            }
        }
    }
}