package com.rds.mews.ui.custom_elements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rds.mews.R
import com.rds.mews.localcore.RSS
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SourcesCard(
    rss: RSS,
    avatarUrl: String?,
    timeText: String,
    onClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    onExpanded: (() -> Unit)? = null
) {
    var cardBounds by remember { mutableStateOf<Rect?>(null) }
    val primaryColor = MaterialTheme.colorScheme.secondaryContainer
    val hasErrors = rss.errCount >= 3
    val menuColor = Color(0xFF2B2D30)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onGloballyPositioned { coordinates ->
                cardBounds = coordinates.boundsInWindow()
            }
            .alpha(if (isExpanded) 0f else 1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    cardBounds?.let { bounds ->
                        onClick(bounds)
                        onExpanded?.invoke()
                    }
                }
            ),
        shape = Shapes.large,
        color = primaryColor,
        shadowElevation = 0.dp
    ) {
        SourcesCardContent(
            rss = rss,
            avatarUrl = avatarUrl,
            timeText = timeText,
            hasErrors = hasErrors,
            menuColor = menuColor
        )
    }
}

@Composable
fun SourcesCardContent(
    rss: RSS,
    avatarUrl: String?,
    timeText: String,
    hasErrors: Boolean,
    menuColor: Color,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showZhdun by remember(avatarUrl) { mutableStateOf(false) }

    LaunchedEffect(avatarUrl) {
        delay(200.milliseconds)
        showZhdun = true
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        if (avatarUrl != null) {
            var isImageLoaded by remember(avatarUrl) { mutableStateOf(false) }
            var retryCount by remember(avatarUrl) { mutableIntStateOf(0) }
            val context = LocalContext.current
            val imageRequest = remember(avatarUrl, retryCount) {
                ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .crossfade(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            }

            if (!isImageLoaded && showZhdun) {
                Image(
                    painter = painterResource(R.drawable.zhdun),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(0.7f)
                )
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { isImageLoaded = true },
                onError = {
                    isImageLoaded = false
                    if (retryCount < 3) {
                        scope.launch {
                            delay((2000L * (retryCount + 1)).milliseconds)
                            retryCount++
                        }
                    }
                }
            )
        } else {
            Image(
                painter = painterResource(R.drawable.zhdun),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(0.7f)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(menuColor.copy(alpha = 0.85f), CircleShape)
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
                .background(menuColor.copy(alpha = 0.85f), CircleShape)
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourcesCardExpansionOverlay(
    rss: RSS,
    buttons: List<TextButtonInputs>,
    avatarUrl: String?,
    timeText: String,
    collapsedBounds: Rect,
    onDismissRequest: () -> Unit,
    onResetErrors: (Long) -> Unit,
    setInBurst: (Boolean) -> Unit,
    setShowMedia: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val hasErrors = rss.errCount >= 3
    val resetErrText = stringResource(R.string.source_reset_errors)
    val doNotTrackText = stringResource(R.string.source_do_not_track)
    val trackText = stringResource(R.string.source_track)
    val showMedia = stringResource(R.string.source_show_media)
    val doNotShowMedia = stringResource(R.string.source_do_not_show_media)

    val imageSideDp = with(density) { collapsedBounds.width.toDp() }
    val actionsMaxHeight = imageSideDp * (3f / 4f)

    val currentButtons = remember(buttons, hasErrors, rss.inBurst, rss.showMedia) {
        val list = mutableListOf<TextButtonInputs>()
        if (hasErrors) {
            list.add(
                TextButtonInputs(
                    text = resetErrText,
                    action = { onResetErrors(rss.id) }
                )
            )
        }
        list.addAll(buttons)
        list.add(
            TextButtonInputs(
                text = if (rss.inBurst) doNotTrackText else trackText,
                action = { setInBurst(!rss.inBurst) }
            )
        )
        list.add(
            TextButtonInputs(
                text = if (rss.showMedia) doNotShowMedia else showMedia,
                action = { setShowMedia(!rss.showMedia) }
            )
        )
        list
    }

    DetachableOverlayCard(
        collapsedBounds = collapsedBounds,
        onDismissRequest = onDismissRequest,
        actionsMaxHeight = actionsMaxHeight,
        modifier = modifier,
        mainContent = {
            SourcesCardContent(
                rss = rss,
                avatarUrl = avatarUrl,
                timeText = timeText,
                hasErrors = hasErrors,
                menuColor = Color(0xFF2B2D30)
            )
        },
        actionsContent = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                currentButtons.forEach { btn ->
                    CustomTextButton(
                        inputs = TextButtonInputs(
                            text = btn.text,
                            action = {
                                btn.action()
                                onDismissRequest()
                            },
                            toast = btn.toast
                        ),
                        defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        shape = Shapes.large
                    )
                }
            }
        }
    )
}

@Composable
fun SourcesAddCard(
    action: () -> Unit,
    transitionState: Boolean? = null
) {
    val buttonTransitionState = remember { androidx.compose.animation.core.MutableTransitionState(transitionState == true) }
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