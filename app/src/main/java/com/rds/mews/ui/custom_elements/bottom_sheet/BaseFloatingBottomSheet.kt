package com.rds.mews.ui.custom_elements.bottom_sheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.ui.custom_elements.customHeader
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseFloatingBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    scope: CoroutineScope,
    maxHeight: Dp = 500.dp,
    stickyHeaderText: String? = null,
    footer: (@Composable (closeSheet: () -> Unit) -> Unit)? = null,
    content: (closeSheet: () -> Unit) -> List<@Composable () -> Unit>
) {
    val closeSheet: () -> Unit = {
        scope.launch { sheetState.hide() }
            .invokeOnCompletion { if (!sheetState.isVisible) onDismissRequest() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(vertical = 24.dp, horizontal = 8.dp)
                .animateContentSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                shape = Shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BottomSheetDefaults.DragHandle()

                    val contentList = content(closeSheet)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (stickyHeaderText != null) {
                            customHeader(
                                text = stickyHeaderText,
                                fontSize = 20.sp,
                                expandable = false,
                                modifier = Modifier.padding(horizontal = 10.dp),
                                showBackground = false
                            )
                        }

                        items(contentList) { itemContent ->
                            Box(modifier = Modifier.padding(horizontal = 10.dp)) {
                                itemContent()
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    if (footer != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            footer(closeSheet)
                        }
                    }
                }
            }
        }
    }
}