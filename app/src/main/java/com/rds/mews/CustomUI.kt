package com.rds.mews

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.window.Popup


@Composable
fun CustomDropdown(
    transitionState: MutableTransitionState<Boolean>,
    buttons: List<Pair<String, () -> Unit>>,
    animDuration: Int = 200
) {
    Surface(
        modifier = Modifier
            .width(150.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
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
        )  {
            Column(
                modifier = Modifier
                    .width(50.dp)
            ) {
                buttons.forEachIndexed { index, button ->
                    CustomDropdownMenuItem(
                        text = button.first,
                        onClick = button.second
                    )

                    if (index < buttons.size - 1) {
                        HorizontalDivider(
                            color = Color.Gray,
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
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun CustomCardWithMenu(
    text: String,
    buttons: List<Pair<String, () -> Unit>>
) {
    val transitionState = remember {
        MutableTransitionState(false).apply {
            targetState = false
        }
    }

    val toggleDropdown = { expanded: Boolean ->
        transitionState.targetState = expanded
    }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier
                .padding(2.dp)
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopEnd)
                ) {
                    IconButton(
                        onClick = { toggleDropdown(!transitionState.targetState) },
                        modifier = Modifier.background(Color.LightGray)
                    ) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Меню")
                    }

                    if (transitionState.currentState || transitionState.targetState) {
                        Popup(
                            onDismissRequest = { toggleDropdown(false) },
                            alignment = Alignment.TopEnd
                        ) {
                            CustomDropdown(
                                transitionState = transitionState,
                                buttons = buttons.map { (label, action) ->
                                    Pair(label) {
                                        action()
                                        toggleDropdown(false)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}