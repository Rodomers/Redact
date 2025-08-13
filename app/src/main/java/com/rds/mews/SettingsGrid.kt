package com.rds.mews

import android.os.Build
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup


@Composable
fun SettingsGrid(modifier: Modifier) {
    var darkTheme by remember { mutableStateOf(false) }
    var adaptiveColors by remember {mutableStateOf(false)}
    val titlesDropdownVisible = remember { MutableTransitionState(false) }
    var titlesNum by remember { mutableStateOf("10 заголовков") }
    val titlesDropdownItems = listOf(
        "10 заголовков" to { titlesNum = "10 заголовков" },
        "25 заголовков" to { titlesNum = "20 заголовков" },
        "50 заголовков" to { titlesNum = "50 заголовков" },
        "75 заголовков" to { titlesNum = "75 заголовков" },
        "100 заголовков" to { titlesNum = "100 заголовков" }
    )
    val limitationDropdownVisible = remember { MutableTransitionState(false) }
    var limitation by remember { mutableStateOf("24 часа") }
    val limitationDropdownItems = listOf(
        "24 часа" to { limitation = "24 часа" },
        "48 часов" to { limitation = "48 часов" },
        "72 часа" to { limitation = "72 часа" },
        "96 часов" to { limitation = "96 часов" },
        "120 часов" to { limitation = "120 часов" }
    )

    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
//        CustomSettingsItem(text = "Тёмная тема") {
//            Switch(checked = darkTheme, onCheckedChange = { darkTheme = it })
//        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            CustomSettingsItem(text = "Адаптивные цвета") {
//                Switch(
//                    checked = adaptiveColors, onCheckedChange = { adaptiveColors = it }
//                )
//            }
//        }
        CustomSettingsItem(text = "Количество заголовков") {
            Box() {
                Button(
                    modifier = Modifier
                        .wrapContentSize()
                        .width(150.dp),
                    onClick = { titlesDropdownVisible.targetState = !titlesDropdownVisible.currentState },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(text = titlesNum)
                }

                if (titlesDropdownVisible.currentState || titlesDropdownVisible.targetState) {
                    Popup(
                        onDismissRequest = { titlesDropdownVisible.targetState = false },
                        alignment = Alignment.TopEnd
                    ) {
                        CustomDropdown(transitionState = titlesDropdownVisible, buttons = titlesDropdownItems)
                    }
                }
            }
        }
        CustomSettingsItem(text = "Срок давности новостей") {
            Box() {
                Button(
                    modifier = Modifier
                        .wrapContentSize()
                        .width(150.dp),
                    onClick = { limitationDropdownVisible.targetState = !limitationDropdownVisible.currentState },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(text = limitation)
                }

                if (limitationDropdownVisible.currentState || limitationDropdownVisible.targetState) {
                    Popup(
                        onDismissRequest = { limitationDropdownVisible.targetState = false },
                        alignment = Alignment.TopEnd
                    ) {
                        CustomDropdown(transitionState = limitationDropdownVisible, buttons = limitationDropdownItems)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun TestSettings() {
    SettingsGrid(modifier = Modifier)
}