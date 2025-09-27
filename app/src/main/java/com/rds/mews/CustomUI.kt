package com.rds.mews

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun CustomDropdown(
    transitionState: MutableTransitionState<Boolean>,
    buttons: List<Pair<String, () -> Unit>>,
    animDuration: Int = 200
) {Surface(
        modifier = Modifier.width(150.dp),
        shape = Shapes.small,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(durationMillis = animDuration),
                clip = false
            ) + fadeIn(),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(durationMillis = animDuration),
                clip = false
            ) + fadeOut()
        ) {
            val onDismiss = remember(transitionState) { { transitionState.targetState = false } }
            Column {
                buttons.forEachIndexed { index, button ->
                    CustomDropdownMenuItem(button.first, button.second, onDismiss = onDismiss)
                    if (index < buttons.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun CustomDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
            .clickable {
                onClick()
                onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun CustomCardWithMenu(
    text: String,
    buttons: List<Pair<String, () -> Unit>>
) {
    val transitionState = remember { MutableTransitionState(false) }
    val toggleDropdown = { transitionState.targetState = !transitionState.currentState }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSecondary,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {toggleDropdown()},
                modifier = Modifier
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.custom_card_with_menu_icon_desc))
            }

            if (transitionState.currentState || transitionState.targetState) {
                Popup(
                    onDismissRequest = { transitionState.targetState = false },
                    alignment = Alignment.TopEnd
                ) {
                    CustomDropdown(transitionState = transitionState, buttons = buttons)
                }
            }
        }
    }
}

@Composable
fun SourcesAddCard(
    action: () -> Unit
) { Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSecondary,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.sources_add_text),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = action,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .fillMaxHeight()
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.sources_add_icon_desc))
            }
        }
    }
}

@Composable
fun TitlesCard(title: Title, showDates: Boolean = false) {
    val clipboardManager = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = 0, initialPageOffsetFraction = 0f, pageCount = {2})
    val coroutineScope = rememberCoroutineScope()
    var pagerContentHeight by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val textSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.onSecondaryContainer,
        backgroundColor = MaterialTheme.colorScheme.onSecondary.copy(alpha=0.8f)
    )
    val source = stringResource(R.string.titles_card_source)
    fun copyText() {
        val copiedText = "${title.title}\n\n${title.text}\n\n${source}: ${title.sources}"
        clipboardManager.setText(AnnotatedString(copiedText))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { expanded = !expanded },
            ) {
                Text(
                    text = getFormattedTimeUnix(title.time, showDates),
                    textAlign = TextAlign.Left,
                    modifier = Modifier
                        .padding(8.dp)
                        .wrapContentWidth()
                )
                Text(
                    text = title.title,
                    textAlign = TextAlign.Left,
                    modifier = Modifier
                        .padding(8.dp)
                        .wrapContentHeight()
                        .align(Alignment.CenterVertically),
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                val pagerModifier = pagerContentHeight?.let {
                    Modifier.height(with(density) { it.toDp() })
                } ?: Modifier.wrapContentHeight()

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp))

                HorizontalPager(
                    state = pagerState,
                    modifier = pagerModifier,
                    verticalAlignment = Alignment.Top
                ) {
                        page ->
                    when (page) {
                        0 -> {
                            CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
                                SelectionContainer {
                                    Text(
                                        text = title.text,
                                        modifier = Modifier.onSizeChanged { newSize ->
                                            pagerContentHeight = newSize.height
                                        }
                                    )
                                }
                            }

                        }
                        1 -> {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .fillMaxSize(),
                                verticalArrangement = Arrangement.Top
                            ) {
                                Text(
                                    text = title.sources, modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    fontWeight = FontWeight.Bold
                                )

                                CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
                                    SelectionContainer {
                                        Text(text = title.links, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Text(
                        text = stringResource(R.string.titles_card_text),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .wrapContentSize()
                            .align(Alignment.CenterVertically)
                            .padding(top = 6.dp, bottom = 6.dp)
                            .clickable(interactionSource, indication = null) {
                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            }
                            .animateContentSize(),
                        fontWeight = if (pagerState.targetPage == 0) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = stringResource(R.string.titles_card_source),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .wrapContentHeight()
                            .align(Alignment.CenterVertically)
                            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
                            .clickable(interactionSource, indication = null) {
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }
                            .animateContentSize(),
                        fontWeight = if (pagerState.targetPage == 1 ) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = ::copyText
                    ) {
                        Icon(
                            modifier = Modifier.size(16.dp),
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share_btn_desc)
                        )
                    }
                }
            }
        }

    }
}

@Composable
fun CustomSettingsItem(text: String, item: @Composable () -> Unit) {
     Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Left,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            item()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPullToRefreshIndicator(
    state: PullToRefreshState,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean
) {
    var indicatorHeight by remember { mutableIntStateOf(0) }

    val refreshThreshold = 80.dp
    val refreshThresholdPx = with(LocalDensity.current) { refreshThreshold.toPx() }
    val scale = if (isRefreshing) 1f else lerp(0f, 1f, state.distanceFraction.coerceIn(0f, 1f))

    val currentUpdatingState by LocalContext.current.observeStringSharedPreference("updating_state", "off").collectAsState("off")
    val text = when {
        currentUpdatingState.contains("/") -> {
            val args = currentUpdatingState.split("/").map { it.toInt() }
            stringResource(R.string.summarizing, args[0], args[1])
        }
        currentUpdatingState == "extracting_topics" -> stringResource(R.string.extracting_topics)
        currentUpdatingState == "updating" -> stringResource(R.string.updating)
        currentUpdatingState == "filtering_topics" -> stringResource(R.string.filtering_topics)
        else -> stringResource(R.string.update)
    }

    Surface(
        modifier = modifier
            .statusBarsPadding()
            .onSizeChanged { size ->
                indicatorHeight = size.height
            }
            .graphicsLayer {
                scaleY = scale
                alpha = scale

                translationY = state.distanceFraction * refreshThresholdPx - indicatorHeight
            }
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .statusBarsPadding()
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = Shapes.large,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            CircularProgressIndicator(
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 16.dp)
            )
//            TextButton(onClick = stopFunc) {
//                Text(stringResource(R.string.restart_updating))
//            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomChangeBottomSheet(
    onDismissRequest: () -> Unit,
    onConfirm: (Pair<String, String>) -> Unit,
    add: Boolean = false,
    source: String = stringResource(R.string.change_dialog_source),
    scope: CoroutineScope,
    sheetState: SheetState
) {
    val title = if (add) stringResource(R.string.change_dialog_add_source) else stringResource(R.string.change_dialog_change_source)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        val closeSheet = {
            scope.launch { sheetState.hide() }
                .invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 16.dp).fillMaxWidth()
            )

            var rssText by remember { mutableStateOf("") }
            var sourceText by remember { mutableStateOf(if (!add) source else "") }
            var validRss by remember { mutableStateOf(!add) }

            if (add) {
                OutlinedTextField(
                    value = rssText,
                    onValueChange = {
                        rssText = it
                        validRss = false
                        scope.launch(Dispatchers.IO) {
                            val res = RSSName(linkTransform(rssText))
                            if (res != null) {
                                sourceText = res
                                validRss = true
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    label = { Text(stringResource(R.string.change_dialog_link)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                    )
                )
            }

            OutlinedTextField(
                value = sourceText,
                onValueChange = { sourceText = it },
                shape = MaterialTheme.shapes.large,
                label = { Text(stringResource(R.string.change_dialog_source)) },
                placeholder = { Text(source) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                )
            )

            if (add) {
                Text(
                    text = if (validRss) stringResource(R.string.valid_link) else stringResource(R.string.enter_correct_link),
                    modifier = Modifier.padding(16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { closeSheet() }
                ) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface)
                }

                if ((validRss && rssText.isNotBlank() && sourceText.isNotBlank() && add)) {
                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            val finalSourceName = sourceText.ifEmpty { source }
                            onConfirm(Pair(finalSourceName, linkTransform(rssText.trim())))
                            closeSheet()
                        },
                        modifier = Modifier.background(color = MaterialTheme.colorScheme.secondaryContainer, shape = Shapes.large)
                    ) {
                        Text(stringResource(R.string.add), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (!add && sourceText.isNotBlank()) {
                    TextButton(
                        onClick = {
                            val finalSourceName = sourceText.ifEmpty { source }
                            onConfirm(Pair(source, finalSourceName))
                            closeSheet()
                        },
                        modifier = Modifier.background(color = MaterialTheme.colorScheme.secondaryContainer, shape = Shapes.large)
                    ) {
                        Text(stringResource(R.string.change), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomErrorBottomSheet(
    title: String,
    text: String,
    confBtnText: String,
    cancelBtnText: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    scope: CoroutineScope,
    sheetState: SheetState
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        val closeSheet = {
            scope.launch { sheetState.hide() }
                .invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                textAlign = TextAlign.Center,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 16.dp).fillMaxWidth()
            )
            Text(
                text = text,
                textAlign = TextAlign.Start,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        closeSheet()
                    }
                ) {
                    Text(cancelBtnText, color = MaterialTheme.colorScheme.onSurface)
                }
                TextButton(
                    onClick = {
                        onConfirm()
                        closeSheet()
                    },
                    modifier = Modifier.background(color = MaterialTheme.colorScheme.secondaryContainer, shape = Shapes.large)
                ) {
                    Text(confBtnText, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun CustomConfirmDialog(
    title: String,
    text: String,
    btnText: String,
    cancelAction: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
    dialogWindowProvider?.window?.setDimAmount(0.6f)

    AlertDialog(
        onDismissRequest = cancelAction,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = Shapes.large,

        title = {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },

        text = {
            Text(text = text)
        },

        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(true)
                }
            ) {
                Text(btnText, color = MaterialTheme.colorScheme.onBackground)
            }
        },

        dismissButton = {
            TextButton(onClick = cancelAction) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onBackground)
            }
        }
    )
}

//@Preview
//@Composable
//fun PreviewDialog() {
//    CustomFullscreenLoading(true)
//}