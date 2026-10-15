package com.rds.mews.ui.custom_elements.bottom_sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rds.mews.R
import com.rds.mews.localcore.TextButtonInputs
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.theme.Shapes
import kotlinx.coroutines.CoroutineScope

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
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = sourceNameValue,
                selection = TextRange(sourceNameValue.length)
            )
        )
    }

    if (sourceNameValue != textFieldValue.text) {
        textFieldValue = TextFieldValue(
            text = sourceNameValue,
            selection = TextRange(sourceNameValue.length)
        )
    }

    val textFieldColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.4f)
    val onTextFieldColor = MaterialTheme.colorScheme.onSecondaryContainer

    BaseFloatingBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scope = scope,
        stickyHeaderText = stringResource(R.string.change_dialog_change_source)
    ) { closeSheet ->
        listOf(
            {
                TextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        onSourceNameChange(it.text)
                    },
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

                    val canChange = originalSourceName.isNotBlank()

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