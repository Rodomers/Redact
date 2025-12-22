package com.rds.mews.ui.custom_elements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rds.mews.R
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.delay

@Composable
fun DeferredUpdateTab(
    transitionState: MutableTransitionState<Boolean>,
    onDismissRequest: () -> Unit,
    animDuration: Int = 200,
    items: List<@Composable () -> Unit>,
    indexes: List<Int>? = null,
    header: String? = null
) {
    LaunchedEffect(transitionState.targetState) {
        if (transitionState.currentState != transitionState.targetState && !transitionState.targetState) {
            delay(timeMillis = animDuration.toLong())
            onDismissRequest()
        }
    }

    if (transitionState.targetState || transitionState.currentState) {
        Dialog(
            onDismissRequest = {
                transitionState.targetState = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(animDuration)),

                exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = animDuration)
                ) + fadeOut(animationSpec = tween(durationMillis = animDuration))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(8.dp),
                    shape = Shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.wrapContentSize()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .padding(8.dp)
                                .heightIn(max = 360.dp)
                        ) {
                            if (header != null) {
                                stickyHeader() {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        LegacyTextDivider(text = header)
                                    }
                                }
                            }

                            items.forEachIndexed { id, it ->
                                if (indexes?.contains(id) ?: true) item { it() }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(4.dp))
                        Row(
                            modifier = Modifier
                                .wrapContentHeight()
                                .fillMaxWidth()
                                .padding()
                                .heightIn(max = 35.dp)
                        ) {
                            IconButton(onClick = { transitionState.targetState = false },
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(start = 8.dp, bottom = 4.dp, top = 0.dp)
                                    .align(Alignment.CenterVertically)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(
                                    R.string.custom_card_with_menu_icon_desc))
                            }
                            Spacer(modifier = Modifier
                                .heightIn(max = 40.dp)
                                .weight(1f))
                        }
                    }
                }
            }
        }
    }
}