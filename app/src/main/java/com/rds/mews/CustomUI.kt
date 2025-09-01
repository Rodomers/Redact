package com.rds.mews

import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                            color = MaterialTheme.colorScheme.secondary,
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
        color = MaterialTheme.colorScheme.primary,
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
                    .background(MaterialTheme.colorScheme.secondary)
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
        color = MaterialTheme.colorScheme.primary,
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
                    .background(MaterialTheme.colorScheme.secondary)
                    .fillMaxHeight()
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.sources_add_icon_desc))
            }
        }
    }
}

@Composable
fun TitlesCard(title: Title, showDates: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = 0, initialPageOffsetFraction = 0f, pageCount = {2})
    val coroutineScope = rememberCoroutineScope()
    var pagerContentHeight by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.primary
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
                        .wrapContentHeight(),
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                val pagerModifier = pagerContentHeight?.let {
                    Modifier.height(with(density) { it.toDp() })
                } ?: Modifier.wrapContentHeight()

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp))

                HorizontalPager(
                    state = pagerState,
                    modifier = pagerModifier,
                    verticalAlignment = Alignment.Top
                ) {
                        page ->
                    when (page) {
                        0 -> {
                            Text(text = title.text,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.onSizeChanged { newSize ->
                                    pagerContentHeight = newSize.height
                                })
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
                                SelectionContainer {
                                    Text(text = title.links, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                ) {
                    Text(
                        text = stringResource(R.string.titles_card_text),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(top = 8.dp, bottom = 6.dp)
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
                            .padding(start = 6.dp, top = 8.dp, bottom = 6.dp)
                            .clickable(interactionSource, indication = null) {
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }
                            .animateContentSize(),
                        fontWeight = if (pagerState.targetPage == 1 ) FontWeight.Bold else FontWeight.Normal
                    )
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
        color = MaterialTheme.colorScheme.primary
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

@Composable
fun CustomFullscreenLoading(isVisible: Boolean, animDuration: Int = 300) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = animDuration)),
        exit = fadeOut(animationSpec = tween(durationMillis = animDuration))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                    indication = null
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = Shapes.large,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp))
                    Text("Обновление...", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 16.dp))
                }
            }
        }
    }
}

//@Composable
//fun CustomPullToRefreshIndicator(modifier: Modifier = Modifier) {
//    Surface(
//        modifier = modifier
//            .fillMaxWidth()
//            .padding(horizontal = 20.dp, vertical = 10.dp),
//        shape = Shapes.large,
//        shadowElevation = 8.dp,
//        color = MaterialTheme.colorScheme.background
//    ) {
//        Column(
//            horizontalAlignment = Alignment.CenterHorizontally,
//            verticalArrangement = Arrangement.Center
//        ) {
//            CircularProgressIndicator(
//                strokeWidth = 3.dp,
//                color = MaterialTheme.colorScheme.primary,
//                modifier = Modifier.padding(top = 16.dp)
//            )
//            Text(
//                text = "Обновление...",
//                fontWeight = FontWeight.Bold,
//                fontSize = 16.sp,
//                modifier = Modifier.padding(horizontal = 40.dp, vertical = 16.dp)
//            )
//        }
//    }
//}

@Composable
fun CustomPullToRefreshIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun CustomChangeDialog(
    cancelAction: () -> Unit,
    onConfirm: (Pair<String, String>) -> Unit,
    add: Boolean = false,
    source: String = stringResource(R.string.change_dialog_source),
    scope: CoroutineScope? = null
) {
    val title = if (add) stringResource(R.string.change_dialog_add_source) else stringResource(R.string.change_dialog_change_source)
    val context = LocalContext.current

    Dialog(onDismissRequest = cancelAction) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setDimAmount(0.6f)

        var rssText by remember { mutableStateOf("") }
        var sourceText by remember { mutableStateOf(if (!add) source else "") }
        var validRss by remember { mutableStateOf(!add) }

        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 16.dp)
                )

                if (add && scope != null) {
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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                            cursorColor = MaterialTheme.colorScheme.onPrimary,
                            focusedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    )
                }

                OutlinedTextField(
                    value = sourceText,
                    onValueChange = { sourceText = it },
                    shape = MaterialTheme.shapes.large,
                    label = { Text(stringResource(R.string.change_dialog_source)) },
                    placeholder = { Text(source) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                        cursorColor = MaterialTheme.colorScheme.onPrimary,
                        focusedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    )
                )

                if (add && scope != null) {
                    Text(
                        text = if (validRss) stringResource(R.string.valid_link) else stringResource(R.string.enter_correct_link),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(16.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = cancelAction) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onPrimary)
                    }

                    if ((validRss && rssText != "" && sourceText != "" && add)) {
                        TextButton(
                            onClick = {
                                val finalSourceName = sourceText.ifEmpty { source }
                                onConfirm(Pair(finalSourceName, linkTransform(rssText.trim())))
                            }
                        ) {
                            Text(stringResource(R.string.add), color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }

                    if (!add && sourceText != "") {
                        TextButton(
                            onClick = {
                                val finalSourceName = sourceText.ifEmpty { source }
                                onConfirm(Pair(source, finalSourceName))
                            }
                        ) {
                            Text(stringResource(R.string.change), color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
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

@Preview
@Composable
fun PreviewDialog() {
    CustomFullscreenLoading(true)
}