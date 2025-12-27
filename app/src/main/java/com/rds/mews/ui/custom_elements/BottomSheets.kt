package com.rds.mews.ui.custom_elements

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.R
import com.rds.mews.localcore.TextButtonInputs
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
                .windowInsetsPadding(WindowInsets.ime)
                .padding(vertical = 24.dp, horizontal = 8.dp)
                .animateContentSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                shape = Shapes.large,
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
                            stickyHeader {
                                Text(
                                    modifier = Modifier
                                        .background(color = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.97f))
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    text = stickyHeaderText,
                                    fontSize = 20.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        items(contentList) { itemContent ->
                            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsListBottomSheet(
    title: String,
    items: List<@Composable () -> Unit>,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    scope: CoroutineScope
) {
    BaseFloatingBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scope = scope,
        stickyHeaderText = title,
        maxHeight = 600.dp,
        content = { _ -> items },
        footer = { closeSheet ->
            Row {
                Spacer(modifier = Modifier.weight(1f))
                CustomTextButton(
                    inputs = TextButtonInputs(
                        text = stringResource(R.string.settings_close),
                        action = { closeSheet() }
                    ),
                    shape = Shapes.large,
                    defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceBottomSheet(
    rssLinkValue: String,
    onRssLinkChange: (String) -> Unit,
    sourceNameValue: String,
    onSourceNameChange: (String) -> Unit,
    isRssValid: Boolean,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    scope: CoroutineScope
) {
    val textFieldsColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.4f)
    val onTextFieldColor = MaterialTheme.colorScheme.onSecondaryContainer

    BaseFloatingBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scope = scope
    ) { closeSheet ->
        listOf(
            {
                Text(
                    text = stringResource(R.string.change_dialog_add_source),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth()
                )
            },
            {
                TextField(
                    value = rssLinkValue,
                    onValueChange = onRssLinkChange,
                    shape = Shapes.large,
                    label = { Text(stringResource(R.string.change_dialog_link)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = textFieldsColor,
                        unfocusedContainerColor = textFieldsColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = onTextFieldColor,
                        focusedLabelColor = onTextFieldColor,
                        unfocusedLabelColor = onTextFieldColor,
                    ),
                    trailingIcon = {
                        if (rssLinkValue.isNotEmpty()) {
                            if (isRssValid) {
                                Icon(Icons.Default.Check, "Valid", tint = MaterialTheme.colorScheme.onSurface)
                            } else {
                                Icon(Icons.Default.Close, "Invalid", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    singleLine = true
                )
            },
            {
                TextField(
                    value = sourceNameValue,
                    onValueChange = onSourceNameChange,
                    shape = Shapes.large,
                    label = { Text(stringResource(R.string.change_dialog_source)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = textFieldsColor,
                        unfocusedContainerColor = textFieldsColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = onTextFieldColor,
                        focusedLabelColor = onTextFieldColor,
                        unfocusedLabelColor = onTextFieldColor,
                    ),
                    singleLine = true
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
                        inputs = TextButtonInputs(
                            text = stringResource(R.string.cancel),
                            action = { closeSheet() }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val canAdd = isRssValid && rssLinkValue.isNotBlank() && sourceNameValue.isNotBlank()

                    CustomTextButton(
                        inputs = TextButtonInputs(
                            text = stringResource(R.string.add),
                            action = {
                                if (canAdd) {
                                    onConfirm()
                                    closeSheet()
                                }
                            }
                        ),
                        shape = Shapes.large,
                        enabled = canAdd,
                        defaultBackgroundColor = if (canAdd) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSourceBottomSheet(
    sourceNameValue: String,
    onSourceNameChange: (String) -> Unit,
    originalSourceName: String,
    onLinkClick: () -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    scope: CoroutineScope
) {
    val textFieldColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.4f)
    val onTextFieldColor = MaterialTheme.colorScheme.onSecondaryContainer

    BaseFloatingBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scope = scope
    ) { closeSheet ->
        listOf(
            {
                Text(
                    text = stringResource(R.string.change_dialog_change_source),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth()
                )
            },
            {
                TextField(
                    value = sourceNameValue,
                    onValueChange = onSourceNameChange,
                    shape = Shapes.large,
                    label = { Text(stringResource(R.string.change_dialog_source)) },
                    placeholder = { Text(originalSourceName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = textFieldColor,
                        unfocusedContainerColor = textFieldColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = onTextFieldColor,
                        focusedLabelColor = onTextFieldColor,
                        unfocusedLabelColor = onTextFieldColor,
                    ),
                    singleLine = true
                )
            },
            {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CustomTextButton(
                        inputs = TextButtonInputs(
                            text = stringResource(R.string.link),
                            action = onLinkClick
                        ),
                        shape = Shapes.large,
                        defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        defaultContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            },
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    CustomTextButton(
                        inputs = TextButtonInputs(
                            text = stringResource(R.string.cancel),
                            action = { closeSheet() }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val canChange = sourceNameValue.isNotBlank()

                    CustomTextButton(
                        inputs = TextButtonInputs(
                            text = stringResource(R.string.change),
                            action = {
                                if (canChange) {
                                    onConfirm()
                                    closeSheet()
                                }
                            }
                        ),
                        shape = Shapes.large,
                        enabled = canChange,
                        defaultBackgroundColor = if (canChange) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                    )
                }
            }
        )
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
    sheetState: SheetState,
    scope: CoroutineScope
) {
    BaseFloatingBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scope = scope
    ) { closeSheet ->
        listOf(
            {
                Text(
                    text = title,
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth()
                )
            },
            {
                Text(
                    text = text,
                    textAlign = TextAlign.Start,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth()
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
                        inputs = TextButtonInputs(
                            text = cancelBtnText,
                            action = { closeSheet() }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    CustomTextButton(
                        inputs = TextButtonInputs(
                            text = confBtnText,
                            action = {
                                onConfirm()
                                closeSheet()
                            }
                        ),
                        shape = Shapes.large,
                        defaultBackgroundColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyBottomSheet(
    apiKeyValue: String,
    onApiKeyChange: (String) -> Unit,
    confirmBtnInputs: TextButtonInputs,
    cancelBtnInputs: TextButtonInputs,
    resetBtnInputs: TextButtonInputs,
    sheetState: SheetState,
    scope: CoroutineScope,
    isApiKeyCorrect: Boolean
) {
    val textFieldColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.4f)
    val onTextFieldColor = MaterialTheme.colorScheme.onSecondaryContainer

    BaseFloatingBottomSheet(
        onDismissRequest = cancelBtnInputs.action,
        sheetState = sheetState,
        scope = scope
    ) { closeSheet ->
        listOf(
            {
                Text(
                    text = stringResource(R.string.settings_sheet_current_key),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth()
                )
            },
            {
                TextField(
                    value = apiKeyValue,
                    onValueChange = onApiKeyChange,
                    shape = Shapes.large,
                    label = { Text(stringResource(R.string.settings_sheet_api_key)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
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
                        if (apiKeyValue.isNotEmpty()) {
                            if (isApiKeyCorrect) {
                                Icon(Icons.Default.Check, "Valid", tint = MaterialTheme.colorScheme.onSurface)
                            } else {
                                Icon(Icons.Default.Close, "Invalid", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    singleLine = true
                )
            },
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CustomTextButton(
                        inputs = resetBtnInputs,
                        shape = Shapes.large,
                        defaultBackgroundColor = MaterialTheme.colorScheme.errorContainer
                    )
                }
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
                        enabled = isApiKeyCorrect
                    )
                }
            }
        )
    }
}