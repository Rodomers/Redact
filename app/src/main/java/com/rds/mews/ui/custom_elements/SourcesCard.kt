package com.rds.mews.ui.custom_elements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rds.mews.R
import com.rds.mews.localcore.IconButtonInputs
import com.rds.mews.localcore.RSS
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.theme.Shapes

@Composable
fun SourcesCard(
    rss: RSS,
    buttons: List<TextButtonInputs>,
    avatarUrl: String?,
    timeText: String,
    onResetErrors: (Long) -> Unit
) {
    val resetText = stringResource(R.string.source_reset_errors)
    val menuTransitionState = remember { MutableTransitionState(false) }

    val blurRadius by animateDpAsState(
        targetValue = if (menuTransitionState.targetState) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 400),
        label = "blurAnimation"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (menuTransitionState.targetState) 0.4f else 1f,
        animationSpec = tween(durationMillis = 400),
        label = "contentAlpha"
    )

    val hasErrors = rss.errCount >= 3
    val primaryColor = MaterialTheme.colorScheme.secondaryContainer

    val currentButtons = remember(buttons, hasErrors) {
        val list = buttons.toMutableList()
        if (hasErrors) {
            list.add(
                TextButtonInputs(
                    text = resetText,
                    action = { onResetErrors(rss.id) }
                )
            )
        }
        list
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = Shapes.large,
        color = primaryColor,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha }
                    .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(Color(0xFF2B2D30).copy(alpha = 0.85f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = rss.currentName ?: rss.originalName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(Color(0xFF2B2D30).copy(alpha = 0.85f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeText,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (hasErrors) Color.Red else Color.Green,
                                shape = CircleShape
                            )
                    )
                }
            }

            CustomIconButton(
                inputs = IconButtonInputs(Icons.Default.MoreVert, action = {
                    menuTransitionState.targetState = !menuTransitionState.targetState
                }),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(64.dp)
                    .padding(12.dp),
                transitionState = menuTransitionState,
                defaultBackgroundColor = Color(0xFF2B2D30).copy(alpha = 0.85f),
                defaultContentColor = Color.White,
                shape = CircleShape
            )

            AnimatedVisibility(
                visibleState = menuTransitionState,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(400)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            menuTransitionState.targetState = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        currentButtons.forEachIndexed { index, btn ->
                            val reverseIndex = currentButtons.size - 1 - index
                            AnimatedVisibility(
                                visible = menuTransitionState.targetState,
                                enter = fadeIn(
                                    animationSpec = tween(durationMillis = 300, delayMillis = index * 50)
                                ) + slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = tween(durationMillis = 300, delayMillis = index * 50)
                                ) + scaleIn(
                                    initialScale = 0.8f,
                                    animationSpec = tween(durationMillis = 300, delayMillis = index * 50)
                                ),
                                exit = fadeOut(
                                    animationSpec = tween(durationMillis = 200, delayMillis = reverseIndex * 50)
                                ) + slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(durationMillis = 200, delayMillis = reverseIndex * 50)
                                ) + scaleOut(
                                    targetScale = 0.8f,
                                    animationSpec = tween(durationMillis = 200, delayMillis = reverseIndex * 50)
                                )
                            ) {
                                CustomTextButton(
                                    inputs = TextButtonInputs(
                                        text = btn.text,
                                        action = {
                                            btn.action()
                                            menuTransitionState.targetState = false
                                        },
                                        toast = btn.toast
                                    ),
                                    defaultBackgroundColor = Color(0xFF2B2D30),
                                    defaultContentColor = Color.White,
                                    shape = CircleShape
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourcesAddCard(
    action: () -> Unit,
    transitionState: Boolean? = null
) {
    val buttonTransitionState = remember { MutableTransitionState(transitionState == true) }
    buttonTransitionState.targetState = transitionState == true

    val primaryColor = MaterialTheme.colorScheme.secondaryContainer

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = Shapes.large,
        color = Color.Transparent,
        border = BorderStroke(4.dp, primaryColor),
        shadowElevation = 0.dp
    ) {
        CustomTextButton(
            inputs = TextButtonInputs(stringResource(R.string.sources_add_text), action),
            modifier = Modifier.fillMaxSize(),
            transitionState = buttonTransitionState,
            transitionBackgroundColor = primaryColor,
            defaultBackgroundColor = Color.Transparent,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
    }
}