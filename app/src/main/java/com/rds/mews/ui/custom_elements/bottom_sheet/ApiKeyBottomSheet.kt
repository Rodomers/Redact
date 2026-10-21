package com.rds.mews.ui.custom_elements.bottom_sheet

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.rds.mews.R
import com.rds.mews.localcore.IconButtonInputs
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.custom_elements.CustomIconButton
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyBottomSheet(
    apiKeys: List<String>,
    onApiKeyChange: (Int, String) -> Unit,
    confirmBtnInputs: TextButtonInputs,
    cancelBtnInputs: TextButtonInputs,
    resetBtnInputs: TextButtonInputs,
    sheetState: SheetState,
    scope: CoroutineScope,
    isApiKeysCorrect: List<Boolean>,
    pagerState: PagerState
) {
    val textFieldColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.4f)
    val onTextFieldColor = MaterialTheme.colorScheme.onSecondaryContainer
    val headerText = stringResource(R.string.settings_sheet_current_key)

    val nonEmptyIndices = apiKeys.indices.filter { apiKeys[it].isNotBlank() }
    val canConfirm = if (nonEmptyIndices.isEmpty()) {
        true
    } else {
        nonEmptyIndices.all { idx -> isApiKeysCorrect.getOrNull(idx) == true }
    }

    val pageCount = pagerState.pageCount
    val settledPage = pagerState.settledPage

    val startPadding by animateDpAsState(
        targetValue = if (pageCount <= 1 || settledPage == 0) 0.dp else 24.dp,
        animationSpec = tween(durationMillis = 200),
        label = "pagerStartPadding"
    )
    val endPadding by animateDpAsState(
        targetValue = if (pageCount <= 1 || settledPage >= pageCount - 1) 0.dp else 24.dp,
        animationSpec = tween(durationMillis = 200),
        label = "pagerEndPadding"
    )

    var deletingIndex by remember { mutableStateOf<Int?>(null) }
    var isDeleteAnimationRunning by remember { mutableStateOf(false) }

    val animProgress by animateFloatAsState(
        targetValue = if (isDeleteAnimationRunning) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        finishedListener = { progress ->
            if (progress >= 1f) {
                deletingIndex?.let { indexToDelete ->
                    if (apiKeys.size <= 1) {
                        onApiKeyChange(0, "")
                    } else {
                        for (i in indexToDelete until apiKeys.size - 1) {
                            onApiKeyChange(i, apiKeys[i + 1])
                        }
                        onApiKeyChange(apiKeys.lastIndex, "")
                    }

                    val newCount = (if (apiKeys.size <= 1) 1 else apiKeys.size - 1).coerceAtLeast(1)
                    if (pagerState.currentPage >= newCount) {
                        scope.launch {
                            pagerState.scrollToPage((newCount - 1).coerceAtLeast(0))
                        }
                    }
                }
                deletingIndex = null
                isDeleteAnimationRunning = false
            }
        },
        label = "deleteAndExpandProgress"
    )

    BaseFloatingBottomSheet(
        onDismissRequest = cancelBtnInputs.action,
        sheetState = sheetState,
        scope = scope,
        stickyHeaderText = headerText
    ) { _ ->
        listOf(
            {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentPadding = PaddingValues(start = startPadding, end = endPadding),
                    pageSpacing = 12.dp,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapPositionalThreshold = 0.15f
                    )
                ) { page ->
                    val currentKey = apiKeys.getOrElse(page) { "" }
                    val isCurrentValid = isApiKeysCorrect.getOrElse(page) { false }

                    var textFieldValue by remember(page, currentKey) {
                        mutableStateOf(
                            TextFieldValue(
                                text = currentKey,
                                selection = TextRange(currentKey.length)
                            )
                        )
                    }

                    if (currentKey != textFieldValue.text) {
                        val safeCursor = textFieldValue.selection.end.coerceIn(0, currentKey.length)
                        textFieldValue = TextFieldValue(
                            text = currentKey,
                            selection = TextRange(safeCursor)
                        )
                    }

                    val isTargetDeleting = deletingIndex == page
                    val isRightmostNew = isDeleteAnimationRunning && page == apiKeys.lastIndex && deletingIndex != page

                    val horizontalScale = when {
                        isTargetDeleting -> (1f - animProgress).coerceIn(0f, 1f)
                        isRightmostNew -> animProgress.coerceIn(0f, 1f)
                        else -> 1f
                    }

                    val slotAlpha = when {
                        isTargetDeleting -> (1f - animProgress).coerceIn(0f, 1f)
                        isRightmostNew -> animProgress.coerceIn(0f, 1f)
                        else -> 1f
                    }

                    TextField(
                        value = textFieldValue,
                        onValueChange = {
                            if (!isDeleteAnimationRunning) {
                                textFieldValue = it
                                onApiKeyChange(page, it.text)
                            }
                        },
                        shape = Shapes.large,
                        label = {
                            Text("${stringResource(R.string.settings_sheet_api_key)} #${page + 1}")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = horizontalScale
                                transformOrigin = if (isTargetDeleting) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                                alpha = slotAlpha
                            },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = textFieldColor,
                            unfocusedContainerColor = textFieldColor,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = onTextFieldColor,
                            focusedLabelColor = onTextFieldColor,
                            unfocusedLabelColor = onTextFieldColor,
                        ),
                        trailingIcon = {
                            if (currentKey.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    if (isCurrentValid) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Valid",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Invalid",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    CustomIconButton(
                                        inputs = IconButtonInputs(
                                            icon = Icons.Default.Delete,
                                            action = {
                                                if (!isDeleteAnimationRunning) {
                                                    deletingIndex = page
                                                    isDeleteAnimationRunning = true
                                                }
                                            }
                                        ),
                                        modifier = Modifier.size(36.dp),
                                        shape = Shapes.large,
                                        enabled = !isDeleteAnimationRunning
                                    )
                                }
                            }
                        },
                        singleLine = true
                    )
                }
            },
            {
                Text(
                    text = stringResource(R.string.settings_sheet_api_hint),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            },
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    CustomTextButton(
                        inputs = cancelBtnInputs
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    CustomTextButton(
                        inputs = confirmBtnInputs,
                        shape = Shapes.large,
                        defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        enabled = canConfirm && !isDeleteAnimationRunning
                    )
                }
            }
        )
    }
}