package com.rds.mews.ui.custom_elements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SourcesCard(
    rss: RSS,
    buttons: List<TextButtonInputs>,
    avatarUrl: String?,
    timeText: String,
    onResetErrors: (Long) -> Unit
) {
    val menuTransitionState = remember { MutableTransitionState(false) }
    val scope = rememberCoroutineScope()

    val blurRadius by animateDpAsState(
        targetValue = if (menuTransitionState.targetState) 16.dp else 0.dp,
        label = "blurAnimation"
    )

    val hasErrors = rss.errCount >= 3
    val primaryColor = MaterialTheme.colorScheme.secondaryContainer

    val currentButtons = remember(buttons, hasErrors) {
        val list = buttons.toMutableList()
        if (hasErrors) {
            list.add(
                TextButtonInputs(
                    text = "Сбросить ошибки",
                    action = {
                        onResetErrors(rss.id)
                    }
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
                    .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
            ) {
                if (avatarUrl != null) {
                    var isImageLoaded by remember { mutableStateOf(false) }
                    var retryCount by remember(avatarUrl) { mutableIntStateOf(0) }

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarUrl)
                            .crossfade(true)
                            .memoryCacheKey("$avatarUrl-$retryCount")
                            .diskCacheKey("$avatarUrl-$retryCount")
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { isImageLoaded = true },
                        onError = {
                            isImageLoaded = false
                            if (retryCount < 3) {
                                scope.launch {
                                    delay(2000L * (retryCount + 1))
                                    retryCount++
                                }
                            }
                        },
                        onLoading = { isImageLoaded = false }
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

                CustomIconButton(
                    inputs = IconButtonInputs(Icons.Default.MoreVert, action = {
                        menuTransitionState.targetState = !menuTransitionState.currentState
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
            }

            AnimatedVisibility(
                visible = menuTransitionState.targetState,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 300)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(primaryColor.copy(alpha = 0.8f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            menuTransitionState.targetState = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        currentButtons.forEachIndexed { index, btn ->
                            AnimatedVisibility(
                                visible = menuTransitionState.targetState,
                                enter = fadeIn(
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        delayMillis = index * 50
                                    )
                                ) + slideInVertically(
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        delayMillis = index * 50
                                    ),
                                    initialOffsetY = { 50 }
                                ),
                                exit = fadeOut(
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        delayMillis = (currentButtons.size - 1 - index) * 30
                                    )
                                ) + slideOutVertically(
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        delayMillis = (currentButtons.size - 1 - index) * 30
                                    ),
                                    targetOffsetY = { 50 }
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