package com.rds.mews.ui.custom_elements.bottom_sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = apiKeyValue,
                selection = TextRange(apiKeyValue.length)
            )
        )
    }

    if (apiKeyValue != textFieldValue.text) {
        textFieldValue = TextFieldValue(
            text = apiKeyValue,
            selection = TextRange(apiKeyValue.length)
        )
    }

    val textFieldColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.4f)
    val onTextFieldColor = MaterialTheme.colorScheme.onSecondaryContainer
    val headerText = stringResource(R.string.settings_sheet_current_key)

    BaseFloatingBottomSheet(
        onDismissRequest = cancelBtnInputs.action,
        sheetState = sheetState,
        scope = scope,
        stickyHeaderText = headerText
    ) { _ ->
        listOf(
            {
                TextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        onApiKeyChange(it.text)
                    },
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
                                Icon(
                                    Icons.Default.Check,
                                    "Valid",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Icon(
                                    Icons.Default.Close,
                                    "Invalid",
                                    tint = MaterialTheme.colorScheme.error
                                )
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