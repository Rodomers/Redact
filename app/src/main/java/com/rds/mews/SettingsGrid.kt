package com.rds.mews

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup


@Composable
fun SettingsGrid(modifier: Modifier, settingsModel: SettingsViewModel) {
//    var darkTheme by remember { mutableStateOf(false) }
//    var adaptiveColors by remember {mutableStateOf(false)}

    val clipboardManager = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var geminiApiText by remember { mutableStateOf(settingsModel.userApi.value) }
    var botApiText by remember { mutableStateOf(settingsModel.userBotApi.value) }

    val colorSchemeDropdownVisible = remember { MutableTransitionState(false) }
    var currentTheme by remember { mutableStateOf(settingsModel.isDarkMode.value) }
    val colorSchemeDropdownItems = mutableListOf(
        "Системная" to { settingsModel.setDarkMode("system")
                       currentTheme = settingsModel.isDarkMode.value },
        "Светлая" to { settingsModel.setDarkMode("light")
                     currentTheme = settingsModel.isDarkMode.value },
        "Тёмная" to { settingsModel.setDarkMode("dark")
                     currentTheme = settingsModel.isDarkMode.value }
    )

    val titlesDropdownVisible = remember { MutableTransitionState(false) }
    val titlesDropdownItems = mutableListOf(
        "10 заголовков" to { settingsModel.setTitlesNum(10) },
        "20 заголовков" to { settingsModel.setTitlesNum(20) },
    )
    if (geminiApiText != "AIzaSyCNNpbcjd8lMRMtD6naikNMaRxnG-0HHkk") {
        titlesDropdownItems.addAll(listOf(
            "30 заголовков" to { settingsModel.setTitlesNum(30) },
            "40 заголовков" to { settingsModel.setTitlesNum(40) },
            "50 заголовков" to { settingsModel.setTitlesNum(50) })
        )
    }
    else if (settingsModel.titlesNum.intValue > 25) settingsModel.setTitlesNum(25)
    val limitationDropdownVisible = remember { MutableTransitionState(false) }
    val limitationDropdownItems = listOf(
        "24 часа" to { settingsModel.setTitlesPeriod(24) },
        "48 часов" to { settingsModel.setTitlesPeriod(48) },
        "72 часа" to { settingsModel.setTitlesPeriod(72) },
        "96 часов" to { settingsModel.setTitlesPeriod(96) },
        "120 часов" to { settingsModel.setTitlesPeriod(120) }
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
        CustomSettingsItem(text = "Тема оформления") {
            Box {
                Button(
                    modifier = Modifier
                        .wrapContentSize()
                        .width(150.dp),
                    onClick = { colorSchemeDropdownVisible.targetState = !colorSchemeDropdownVisible.currentState },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(text = when (currentTheme) {
                        "light" -> "Светлая"
                        "dark" -> "Тёмная"
                        else -> "Системная"
                    })
                }

                if (colorSchemeDropdownVisible.currentState || colorSchemeDropdownVisible.targetState) {
                    Popup(
                        onDismissRequest = { colorSchemeDropdownVisible.targetState = false },
                        alignment = Alignment.TopEnd
                    ) {
                        CustomDropdown(transitionState = colorSchemeDropdownVisible, buttons = colorSchemeDropdownItems)
                    }
                }
            }
        }
        CustomSettingsItem(text = "Максимум заголовков") {
            Box {
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
                    Text(text = "${settingsModel.titlesNum.intValue} заголовков")
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
            Box {
                Button(
                    modifier = Modifier
                        .wrapContentSize()
                        .width(150.dp),
                    onClick = { limitationDropdownVisible.targetState = !limitationDropdownVisible.currentState },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {Text(text = "${settingsModel.titlesPeriod.intValue} час${
                        if (settingsModel.titlesPeriod.intValue % 10 >= 5 || settingsModel.titlesPeriod.intValue % 10 == 0) "ов" else if (settingsModel.titlesPeriod.intValue % 10 != 1) "а" else ""
                    }")
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
        CustomSettingsItem(text = "Ключ Gemini API") {
            Box {
                Button(
                    modifier = Modifier
                        .wrapContentSize()
                        .widthIn(min = 150.dp, max = 250.dp),
                    onClick = {
                        when (geminiApiText) {
                            "AIzaSyCNNpbcjd8lMRMtD6naikNMaRxnG-0HHkk" -> {
                                val clipboardText: AnnotatedString? = clipboardManager.getText()

                                clipboardText?.let {
                                    text += it.text
                                }

                                settingsModel.setUserGeminiApi(text)
                                geminiApiText = text
                                text = ""
                            }
                            else -> {
                                settingsModel.setUserGeminiApi("AIzaSyCNNpbcjd8lMRMtD6naikNMaRxnG-0HHkk")
                                geminiApiText = settingsModel.userApi.value
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(text = if (geminiApiText != "AIzaSyCNNpbcjd8lMRMtD6naikNMaRxnG-0HHkk") "Сброс" else "Вставить")
                }
            }
        }

        CustomSettingsItem(text = "Ключ Telegram Bot API") {
            Box {
                Button(
                    modifier = Modifier
                        .wrapContentSize()
                        .widthIn(min = 150.dp, max = 250.dp),
                    onClick = {
                        when (botApiText) {
                            "" -> {
                                val clipboardText: AnnotatedString? = clipboardManager.getText()

                                clipboardText?.let {
                                    text += it.text
                                }

                                settingsModel.setUserBotApi(text)
                                botApiText = text
                                text = ""
                            }
                            else -> {
                                settingsModel.setUserBotApi()
                                botApiText = settingsModel.userBotApi.value
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(text = if (botApiText != "") "Сброс" else "Вставить")
                }
            }
        }
    }
}