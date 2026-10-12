package com.rds.mews.ui.custom_elements.bottom_sheet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rds.mews.R
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReorderableButtonsBottomSheet(
    items: List<Pair<String, Int>>,
    onSavePositions: (String, Int) -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    scope: CoroutineScope,
    title: String = stringResource(R.string.settings_themes),
    maxHeight: Dp = 500.dp
) {
    var itemsList by remember(items) {
        val selected = items.filter { it.second > 0 }.sortedBy { it.second }.map { it.first }
        val unselected = items.filter { it.second <= 0 }.map { it.first }.sorted()
        mutableStateOf(selected + unselected)
    }

    val selectedKeys = remember(items) {
        items.filter { it.second > 0 }.map { it.first }.toMutableStateList()
    }

    var draggedKey by remember { mutableStateOf<String?>(null) }
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }

    fun restructureItems(activeList: List<String>) {
        val selected = itemsList.filter { activeList.contains(it) }
        val unselected = itemsList.filter { !activeList.contains(it) }.sorted()
        val targetList = selected + unselected

        if (itemsList != targetList) {
            itemsList = targetList
        }
    }

    BaseFloatingBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scope = scope,
        maxHeight = maxHeight,
        stickyHeaderText = title,
        footer = { closeSheet ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                CustomTextButton(
                    inputs = TextButtonInputs(
                        text = stringResource(R.string.cancel),
                        action = { closeSheet() }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                CustomTextButton(
                    inputs = TextButtonInputs(
                        text = stringResource(R.string.settings_sheet_api_save),
                        action = {
                            var currentPlace = 1
                            itemsList.forEach { name ->
                                if (selectedKeys.contains(name)) {
                                    onSavePositions(name, currentPlace)
                                    currentPlace++
                                } else {
                                    onSavePositions(name, 0)
                                }
                            }
                            closeSheet()
                        }
                    ),
                    shape = Shapes.large,
                    defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer
                )
            }
        },
        content = { _ ->
            listOf {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial
                                )
                                val downPos = down.position
                                val hit = itemBounds.entries.firstOrNull { (_, rect) -> rect.contains(downPos) }
                                val itemKey = hit?.key ?: return@awaitEachGesture

                                var isDrag = false
                                val pointerId = down.id

                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                                    if (!change.pressed) {
                                        if (isDrag) {
                                            change.consume()
                                        }
                                        draggedKey = null
                                        break
                                    }

                                    val dragDelta = change.position - downPos
                                    if (!isDrag && dragDelta.getDistance() > viewConfiguration.touchSlop) {
                                        isDrag = true
                                        draggedKey = itemKey
                                        if (!selectedKeys.contains(itemKey)) {
                                            selectedKeys.add(itemKey)
                                            restructureItems(selectedKeys.toList())
                                        }
                                    }

                                    if (isDrag) {
                                        change.consume()
                                        val currentPos = change.position

                                        val targetEntry = itemBounds.entries.firstOrNull { (key, rect) ->
                                            key != draggedKey && selectedKeys.contains(key) && rect.contains(currentPos)
                                        }

                                        if (targetEntry != null && draggedKey != null) {
                                            val currentKey = draggedKey!!
                                            val fromIndex = itemsList.indexOf(currentKey)
                                            val toIndex = itemsList.indexOf(targetEntry.key)

                                            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                                                val newList = itemsList.toMutableList()
                                                val moved = newList.removeAt(fromIndex)
                                                newList.add(toIndex, moved)
                                                itemsList = newList
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsList.forEach { itemKey ->
                            val isSelected = selectedKeys.contains(itemKey)
                            val isDragged = (draggedKey == itemKey)

                            key(itemKey) {
                                val animOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
                                var lastLayoutPos by remember { mutableStateOf<Offset?>(null) }
                                val itemScope = rememberCoroutineScope()

                                val scale by animateFloatAsState(
                                    targetValue = if (isDragged) 1.15f else 1f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "scale"
                                )

                                val buttonBgColor by animateColorAsState(
                                    targetValue = if (isSelected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    animationSpec = tween(durationMillis = 200),
                                    label = "bgColor"
                                )

                                CustomTextButton(
                                    inputs = TextButtonInputs(
                                        text = itemKey,
                                        action = {
                                            val currentlySelected = selectedKeys.contains(itemKey)
                                            if (currentlySelected) {
                                                selectedKeys.remove(itemKey)
                                            } else {
                                                selectedKeys.add(itemKey)
                                            }
                                            restructureItems(selectedKeys.toList())
                                        }
                                    ),
                                    shape = Shapes.large,
                                    defaultBackgroundColor = buttonBgColor,
                                    modifier = Modifier
                                        .zIndex(if (isDragged) 10f else 1f)
                                        .onGloballyPositioned { coordinates ->
                                            if (!coordinates.isAttached) return@onGloballyPositioned
                                            val currentPos = coordinates.positionInParent()

                                            if (currentPos.x == 0f && currentPos.y == 0f && coordinates.size.width == 0) {
                                                return@onGloballyPositioned
                                            }

                                            itemBounds[itemKey] = Rect(
                                                currentPos.x,
                                                currentPos.y,
                                                currentPos.x + coordinates.size.width.toFloat(),
                                                currentPos.y + coordinates.size.height.toFloat()
                                            )

                                            val prev = lastLayoutPos
                                            if (prev != null && prev != currentPos) {
                                                val visualDelta = (prev + animOffset.value) - currentPos
                                                itemScope.launch {
                                                    animOffset.snapTo(visualDelta)
                                                    animOffset.animateTo(
                                                        targetValue = Offset.Zero,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = 500f
                                                        )
                                                    )
                                                }
                                            }
                                            lastLayoutPos = currentPos
                                        }
                                        .graphicsLayer {
                                            translationX = animOffset.value.x
                                            translationY = animOffset.value.y
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}