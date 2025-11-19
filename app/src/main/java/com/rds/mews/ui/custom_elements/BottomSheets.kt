package com.rds.mews.ui.custom_elements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.MewsRepository
import com.rds.mews.R
import com.rds.mews.RSSName
import com.rds.mews.linkTransform
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.text.ifEmpty

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomChangeBottomSheet(
    onDismissRequest: () -> Unit,
    onConfirm: (Pair<String, String>) -> Unit,
    add: Boolean = false,
    source: String = stringResource(R.string.change_dialog_source),
    scope: CoroutineScope,
    sheetState: SheetState,
    sourceLink: String? = null
) {
    val title = if (add) stringResource(R.string.change_dialog_add_source) else stringResource(R.string.change_dialog_change_source)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
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
                modifier = Modifier
                    .padding(horizontal = 40.dp, vertical = 16.dp)
                    .fillMaxWidth()
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
                            val res = RSSName(
                                linkTransform(rssText),
                                enableProxy = MewsRepository.proxyEnabled.value
                            )
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

            if (!add && sourceLink != null) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = buildAnnotatedString {
                        append(stringResource(R.string.link))

                        withLink(
                            link = LinkAnnotation.Url(
                                url = sourceLink,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        textDecoration = TextDecoration.Underline
                                    )
                                )
                            )
                        ) {
                            append(source)
                        }
                    }
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
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
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
                modifier = Modifier
                    .padding(horizontal = 40.dp, vertical = 16.dp)
                    .fillMaxWidth()
            )
            Text(
                text = text,
                textAlign = TextAlign.Start,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth()
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