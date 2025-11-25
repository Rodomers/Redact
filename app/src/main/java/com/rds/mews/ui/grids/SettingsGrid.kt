package com.rds.mews.ui.grids

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rds.mews.MainActivity
import com.rds.mews.R
import com.rds.mews.viewmodels.SettingsViewModel
import com.rds.mews.localcore.intTimeToStr
import com.rds.mews.localcore.requestNotificationPermission
import com.rds.mews.ui.custom_elements.CustomBottomFootnote
import com.rds.mews.ui.custom_elements.CustomErrorBottomSheet
import com.rds.mews.ui.custom_elements.CustomSettingsItem
import com.rds.mews.ui.custom_elements.CustomSwitch
import com.rds.mews.ui.custom_elements.CustomTextDivider
import com.rds.mews.ui.custom_elements.DeferredUpdateTab
import com.rds.mews.ui.custom_elements.CustomDropdown
import com.rds.mews.ui.theme.Shapes


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGrid(gridState: LazyGridState, modifier: Modifier, settingsModel: SettingsViewModel, mainActivity: MainActivity) {
    val clipboardManager = LocalClipboardManager.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current

    var text by remember { mutableStateOf("") }
    val showDates by settingsModel.showDates.collectAsStateWithLifecycle()
    val compactTab by settingsModel.compactTabBar.collectAsStateWithLifecycle()
    val monetColors by settingsModel.isMonetColors.collectAsStateWithLifecycle()
    val filterTopics by settingsModel.filterTopics.collectAsStateWithLifecycle()
    val titlesNum by settingsModel.titlesNum.collectAsStateWithLifecycle()
    val defaultGeminiApiKey by remember { mutableStateOf(settingsModel.defaultApiKey) }
    val geminiApiText by settingsModel.userApi.collectAsStateWithLifecycle()
    val currentLlmModel by settingsModel.currentLlm.collectAsStateWithLifecycle()
    val titlesPeriod by settingsModel.titlesPeriod.collectAsStateWithLifecycle()
    val rssUpdateInterval by settingsModel.rssUpdateInterval.collectAsStateWithLifecycle()
    val endureTime by settingsModel.endureTime.collectAsStateWithLifecycle()
    val titlesAlarmUpdate by settingsModel.titlesAlarmUpdate.collectAsStateWithLifecycle()
    val alarmMins by settingsModel.titlesAlarmMins.collectAsStateWithLifecycle()
    val alarmFrequency by settingsModel.titlesUpdateFrequency.collectAsStateWithLifecycle()
    val bannedNews by settingsModel.bannedNews.collectAsStateWithLifecycle()
    val proxyEnabled by settingsModel.proxyEnabled.collectAsStateWithLifecycle()
    var alarmHrsText by remember { mutableIntStateOf(alarmMins / 60) }
    var alarmMinsText by remember { mutableIntStateOf(alarmMins % 60) }
    var showAlarmsSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(alarmMins) {
        alarmHrsText = alarmMins / 60
        alarmMinsText = alarmMins % 60
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val colorSchemeDropdownVisible = remember { MutableTransitionState(false) }
    val currentTheme by settingsModel.currentTheme.collectAsStateWithLifecycle()
    val colorSchemeDropdownItems = mutableListOf(
        stringResource(R.string.settings_system_theme) to { settingsModel.setCurrentTheme("system") },
        stringResource(R.string.settings_light_theme) to { settingsModel.setCurrentTheme("light") },
        stringResource(R.string.settings_dark_theme) to { settingsModel.setCurrentTheme("dark") }
    )

    val titlesDropdownVisible = remember { MutableTransitionState(false) }
    val titlesDropdownItems = mutableListOf(
        pluralStringResource(R.plurals.titles, count = 10, 10) to { settingsModel.setTitlesNum(10) },
        pluralStringResource(R.plurals.titles, count = 20, 20) to { settingsModel.setTitlesNum(20) },
    )
    if (geminiApiText != defaultGeminiApiKey) {
        titlesDropdownItems.addAll(listOf(
            pluralStringResource(R.plurals.titles, count = 30, 30) to { settingsModel.setTitlesNum(30) },
            pluralStringResource(R.plurals.titles, count = 40, 40) to { settingsModel.setTitlesNum(40) },
            pluralStringResource(R.plurals.titles, count = 50, 50) to { settingsModel.setTitlesNum(50) })
        )
    }
    else if (titlesNum > 20) settingsModel.setTitlesNum(20)

    val limitationDropdownVisible = remember { MutableTransitionState(false) }
    val limitationDropdownItems = listOf(
        pluralStringResource(R.plurals.hours, count = 12, 12) to { settingsModel.setTitlesPeriod(12) },
        pluralStringResource(R.plurals.hours, count = 24, 24) to { settingsModel.setTitlesPeriod(24) },
        pluralStringResource(R.plurals.hours, count = 48, 48) to { settingsModel.setTitlesPeriod(48) },
        pluralStringResource(R.plurals.hours, count = 72, 72) to { settingsModel.setTitlesPeriod(72) },
        pluralStringResource(R.plurals.hours, count = 96, 96) to { settingsModel.setTitlesPeriod(96) },
        pluralStringResource(R.plurals.hours, count = 120, 120) to { settingsModel.setTitlesPeriod(120) }
    )

    val rssUpdateDropdownVisible = remember { MutableTransitionState(false) }
    val rssUpdateDropdownItems = listOf(
        pluralStringResource(R.plurals.minutes, count = 15, 15) to { settingsModel.setRssUpdateInterval(context, 15) },
        pluralStringResource(R.plurals.minutes, count = 30, 30) to { settingsModel.setRssUpdateInterval(context, 30) },
        pluralStringResource(R.plurals.minutes, count = 60, 60) to { settingsModel.setRssUpdateInterval(context, 60) }
    )

    val geminiModelDropdownVisible = remember { MutableTransitionState(false) }
    var geminiModelText by remember { mutableStateOf("") }
    geminiModelText = when (currentLlmModel) {
        "gemini-2.5-flash" -> "2.5 Flash"
        "gemini-2.0-flash" -> "2.0 Flash"
        "gemini-2.0-flash-lite" -> "2.0 Flash Lite"
        else -> "2.5 Flash Lite"
    }
    val geminiModelDropdownItems = listOf(
        "2.5 Flash" to { settingsModel.setCurrentLlm("gemini-2.5-flash") },
        "2.5 Flash Lite" to { settingsModel.setCurrentLlm("gemini-2.5-flash-lite") },
        "2.0 Flash" to { settingsModel.setCurrentLlm("gemini-2.0-flash") },
        "2.0 Flash Lite" to { settingsModel.setCurrentLlm("gemini-2.0-flash-lite") }
    )

    val alarmHoursListVisible = remember { MutableTransitionState(false) }
    val alarmHrsItems = (0..23).toList().map { num ->
        intTimeToStr(num) to {
            alarmHrsText = num
            settingsModel.setTitlesAlarmMins(context, alarmHrsText * 60 + alarmMinsText)
        }
    }
    val alarmMinutesListVisible = remember { MutableTransitionState(false) }
    val alarmMinsItems = (0..59).toList().map {num ->
        intTimeToStr(num) to {
            alarmMinsText = num
            settingsModel.setTitlesAlarmMins(context, alarmHrsText * 60 + alarmMinsText)
        }
    }

    val titlesFrequencyListVisible = remember { MutableTransitionState(false) }
    val titlesFrequencyItems = listOf (
        pluralStringResource(R.plurals.hours, count = 12, 12) to { settingsModel.setTitlesUpdFrequency(context, 12) },
        pluralStringResource(R.plurals.hours, count = 24, 24) to { settingsModel.setTitlesUpdFrequency(context, 24) },
        pluralStringResource(R.plurals.hours, count = 48, 48) to { settingsModel.setTitlesUpdFrequency(context, 48) },
        pluralStringResource(R.plurals.hours, count = 72, 72) to { settingsModel.setTitlesUpdFrequency(context, 72) },
        pluralStringResource(R.plurals.hours, count = 96, 96) to { settingsModel.setTitlesUpdFrequency(context, 96) },
        pluralStringResource(R.plurals.hours, count = 120, 120) to { settingsModel.setTitlesUpdFrequency(context, 120) }
    )

    val autoUpdateScreenState = remember { MutableTransitionState(false) }
    val autoUpdateIndexes = remember { mutableStateOf<List<Int>?>(listOf(0)) }
    LaunchedEffect(titlesAlarmUpdate) {
        autoUpdateIndexes.value = if (titlesAlarmUpdate) null else listOf(0)
    }
    val autoUpdateItems: List<@Composable () -> Unit> = listOf(
        {
            Spacer(modifier = Modifier.height(12.dp))

            CustomSettingsItem(text = stringResource(R.string.settings_titles_alarm_update)) {
                CustomSwitch(
                    checked = titlesAlarmUpdate,
                    onCheckedChange = {
                        settingsModel.setTitlesAlarmUpdate(
                            context = context,
                            activity = mainActivity,
                            onShowNotificationsSheet = { showNotificationsSheet = true },
                            onShowAlarmsSheet = { showAlarmsSheet = true },
                            value = it
                        )
                    }
                )
            }
        },
        {
            CustomSettingsItem(text = stringResource(R.string.settings_titles_update_frequency)) {
                var frequencyAnchorBounds by remember { mutableStateOf<IntRect?>(null) }

                Box {
                    Button(
                        modifier = Modifier
                            .width(150.dp)
                            .wrapContentHeight()
                            .onGloballyPositioned { frequencyAnchorBounds = it.boundsInWindow().roundToIntRect() },
                        onClick = {
                            titlesFrequencyListVisible.targetState =
                                !titlesFrequencyListVisible.currentState
                        },
                        shape = Shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.hours,
                                alarmFrequency,
                                alarmFrequency
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    if (titlesFrequencyListVisible.currentState || titlesFrequencyListVisible.targetState) {
                        CustomDropdown(
                            transitionState = titlesFrequencyListVisible,
                            buttons = titlesFrequencyItems.map { (text, action) ->
                                text to { action() }
                            },
                            inputBounds = frequencyAnchorBounds,
                            config = config,
                            density = density,
                            onDismissRequest = {
                                titlesFrequencyListVisible.targetState = false
                            },
                            centeredArrow = true
                        )
                    }
                }
            }
        },
        {
            CustomSettingsItem(text = stringResource(R.string.settings_titles_update_time)) {
                var hoursAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
                var minsAnchorBounds by remember { mutableStateOf<IntRect?>(null) }

                Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            modifier = Modifier
                                .width(70.dp)
                                .wrapContentHeight()
                                .onGloballyPositioned {
                                    hoursAnchorBounds = it.boundsInWindow().roundToIntRect()
                                },
                            onClick = {
                                alarmHoursListVisible.targetState =
                                    !alarmHoursListVisible.currentState
                            },
                            shape = Shapes.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Text(
                                text = intTimeToStr(alarmHrsText),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = ":",
                            fontSize = 22.sp,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        Button(
                            modifier = Modifier
                                .width(70.dp)
                                .wrapContentHeight()
                                .onGloballyPositioned {
                                    minsAnchorBounds = it.boundsInWindow().roundToIntRect()
                                },
                            onClick = {
                                alarmMinutesListVisible.targetState =
                                    !alarmMinutesListVisible.currentState
                            },
                            shape = Shapes.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Text(
                                text = intTimeToStr(alarmMinsText),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    if (alarmHoursListVisible.currentState || alarmHoursListVisible.targetState) {
                        CustomDropdown(
                            transitionState = alarmHoursListVisible,
                            buttons = alarmHrsItems.mapNotNull { (text, action) ->
                                if (!(alarmFrequency == 12 && text.toInt() > 12)) text to { action() } else null
                            },
                            inputBounds = hoursAnchorBounds,
                            config = config,
                            density = density,
                            onDismissRequest = {
                                alarmHoursListVisible.targetState = false
                            },
                            timeList = true
                        )
                    }
                    if (alarmMinutesListVisible.currentState || alarmMinutesListVisible.targetState) {
                        CustomDropdown(
                            transitionState = alarmMinutesListVisible,
                            buttons = alarmMinsItems.map { (text, action) ->
                                text to { action() }
                            },
                            inputBounds = minsAnchorBounds,
                            config = config,
                            density = density,
                            onDismissRequest = {
                                alarmMinutesListVisible.targetState = false
                            },
                            timeList = true
                        )
                    }
                }
            }
        }
    )

    val bannedNewsScreenState = remember { MutableTransitionState(false) }
    val bannedNewsItems: MutableList<@Composable () -> Unit> = mutableListOf()
    bannedNews.forEach { it ->
        if (it != ""){
            bannedNewsItems.add(
                {
                    CustomSettingsItem(it) {
                        IconButton(
                            onClick = { settingsModel.delBannedNews(it) }
                        ) {
                            Icon(
                                modifier = Modifier.size(16.dp),
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.ban_btn_desc)
                            )
                        }
                    }
                }
            )
        }
    }
    LaunchedEffect(bannedNewsItems) {
        if (bannedNewsItems.isEmpty()) bannedNewsScreenState.targetState = false
    }

    if (showAlarmsSheet) {
        CustomErrorBottomSheet(
            title = stringResource(R.string.settings_alarms_sheet_title),
            text = stringResource(R.string.settings_alarms_sheet_text),
            confBtnText = stringResource(R.string.settings_alarms_sheet_conf),
            cancelBtnText = stringResource(R.string.settings_alarms_sheet_cancel),
            onDismissRequest = { showAlarmsSheet = false },
            onConfirm = {
                showAlarmsSheet = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also { intent ->
                        mainActivity.startActivity(intent)
                    }
                }
            },
            scope = scope,
            sheetState = bottomSheetState
        )
    }

    if (showNotificationsSheet) {
        CustomErrorBottomSheet(
            title = stringResource(R.string.settings_notification_sheet_title),
            text = stringResource(R.string.settings_notification_sheet_text),
            confBtnText = stringResource(R.string.settings_notification_sheet_conf),
            cancelBtnText = stringResource(R.string.settings_notification_sheet_cancel),
            onDismissRequest = { showNotificationsSheet = false },
            onConfirm = {
                showAlarmsSheet = false
                requestNotificationPermission(mainActivity)
            },
            scope = scope,
            sheetState = bottomSheetState
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            contentPadding = WindowInsets.statusBars.asPaddingValues(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            state = gridState
        ) {
            stickyHeader() {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomTextDivider(text = stringResource(R.string.settings_chapter_appearance))
                }
            }
            item {
                CustomSettingsItem(text = stringResource(R.string.settings_compact_tab)) {
                    Switch(
                        checked = compactTab,
                        onCheckedChange = { settingsModel.setCompactTab(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.background,
                            checkedTrackColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            checkedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer,

                            uncheckedThumbColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            uncheckedTrackColor = MaterialTheme.colorScheme.background,
                            uncheckedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    CustomSettingsItem(text = stringResource(R.string.settings_monet_colors)) {
                        CustomSwitch(
                            checked = monetColors,
                            onCheckedChange = { settingsModel.setMonetColors(it) }
                        )
                    }
                }
            }
            item {
                CustomSettingsItem(text = stringResource(R.string.settings_color_scheme)) {
                    var colorSchemeBounds by remember { mutableStateOf<IntRect?>(null) }

                    Box {
                        Button(
                            modifier = Modifier
                                .wrapContentSize()
                                .width(150.dp)
                                .onGloballyPositioned { colorSchemeBounds = it.boundsInWindow().roundToIntRect() },
                            onClick = {
                                colorSchemeDropdownVisible.targetState =
                                    !colorSchemeDropdownVisible.currentState
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Text(
                                text = when (currentTheme) {
                                    "light" -> stringResource(R.string.settings_light_theme)
                                    "dark" -> stringResource(R.string.settings_dark_theme)
                                    else -> stringResource(R.string.settings_system_theme)
                                }, color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        if (colorSchemeDropdownVisible.currentState || colorSchemeDropdownVisible.targetState) {
                            CustomDropdown(
                                transitionState = colorSchemeDropdownVisible,
                                buttons = colorSchemeDropdownItems.map { (text, action) ->
                                    text to { action() }
                                },
                                inputBounds = colorSchemeBounds,
                                config = config,
                                density = density,
                                onDismissRequest = {
                                    colorSchemeDropdownVisible.targetState = false
                                },
                                centeredArrow = true
                            )
                        }
                    }
                }
            }

            stickyHeader() {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomTextDivider(text = stringResource(R.string.settings_chapter_titles))
                }
            }
            item {
                CustomSettingsItem(text = stringResource(R.string.settings_show_dates)) {
                    CustomSwitch(
                        checked = showDates,
                        onCheckedChange = { settingsModel.setShowDates(it) }
                    )
                }
            }
            item {
                CustomSettingsItem(text = stringResource(R.string.settings_endure_time)) {
                    CustomSwitch(
                        checked = endureTime,
                        onCheckedChange = { settingsModel.setEndureTime(it) }
                    )
                }
            }
            item {
                CustomSettingsItem(text = stringResource(R.string.settings_maximum_headers)) {
                    var maximumHeadersBounds by remember { mutableStateOf<IntRect?>(null) }

                    Box {
                        Button(
                            modifier = Modifier
                                .wrapContentSize()
                                .width(150.dp)
                                .onGloballyPositioned { maximumHeadersBounds = it.boundsInWindow().roundToIntRect() },
                            onClick = {
                                titlesDropdownVisible.targetState =
                                    !titlesDropdownVisible.currentState
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.titles,
                                    count = titlesNum,
                                    titlesNum
                                ),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        if (titlesDropdownVisible.currentState || titlesDropdownVisible.targetState) {
                            CustomDropdown(
                                transitionState = titlesDropdownVisible,
                                buttons = titlesDropdownItems.map { (text, action) ->
                                    text to { action() }
                                },
                                inputBounds = maximumHeadersBounds,
                                config = config,
                                density = density,
                                onDismissRequest = { titlesDropdownVisible.targetState = false },
                                centeredArrow = true
                            )
                        }
                    }
                }
            }
            item {
                CustomSettingsItem(text = stringResource(R.string.settings_news_period)) {
                    var newsPeriodBounds by remember { mutableStateOf<IntRect?>(null) }

                    Box {
                        Button(
                            modifier = Modifier
                                .wrapContentSize()
                                .width(150.dp)
                                .onGloballyPositioned{ newsPeriodBounds = it.boundsInWindow().roundToIntRect() },
                            onClick = {
                                limitationDropdownVisible.targetState =
                                    !limitationDropdownVisible.currentState
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.hours,
                                    count = titlesPeriod,
                                    titlesPeriod
                                ),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        if (limitationDropdownVisible.currentState || limitationDropdownVisible.targetState) {
                            CustomDropdown(
                                transitionState = limitationDropdownVisible,
                                buttons = limitationDropdownItems.map { (text, action) ->
                                    text to { action() }
                                },
                                inputBounds = newsPeriodBounds,
                                config = config,
                                density = density,
                                onDismissRequest = { limitationDropdownVisible.targetState = false },
                                centeredArrow = true
                            )
                        }
                    }
                }
            }
            item {
                CustomSettingsItem(text = stringResource(R.string.settings_news_update_frequency)) {
                    var newsUpdateFreqBounds by remember { mutableStateOf<IntRect?>(null) }

                    Box {
                        Button(
                            modifier = Modifier
                                .wrapContentSize()
                                .width(150.dp)
                                .onGloballyPositioned{ newsUpdateFreqBounds = it.boundsInWindow().roundToIntRect() },
                            onClick = {
                                rssUpdateDropdownVisible.targetState =
                                    !rssUpdateDropdownVisible.currentState
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.minutes,
                                    count = rssUpdateInterval,
                                    rssUpdateInterval
                                ),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        if (rssUpdateDropdownVisible.currentState || rssUpdateDropdownVisible.targetState) {
                            CustomDropdown(
                                transitionState = rssUpdateDropdownVisible,
                                buttons = rssUpdateDropdownItems,
                                inputBounds = newsUpdateFreqBounds,
                                config = config,
                                density = density,
                                onDismissRequest = { rssUpdateDropdownVisible.targetState = false },
                                centeredArrow = true
                            )
                        }
                    }
                }
            }
            item {
                CustomSettingsItem(text = stringResource(R.string.settings_titles_auto_update)) {
                    IconButton(
                        modifier = Modifier
                            .wrapContentSize(),
                        onClick = {
                            autoUpdateScreenState.targetState = !autoUpdateScreenState.currentState
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.custom_card_with_menu_icon_desc)
                        )
                    }
                }
            }

            stickyHeader() {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomTextDivider(text = stringResource(R.string.settings_chapter_llm))
                }
            }
            if (geminiApiText != defaultGeminiApiKey) {
                item {
                    CustomSettingsItem(text = stringResource(R.string.settings_filter_topics)) {
                        CustomSwitch(
                            checked = filterTopics,
                            onCheckedChange = { settingsModel.setFilterTopics(it) }
                        )
                    }
                }
            }
            if (geminiApiText != defaultGeminiApiKey) {
                item {
                    var geminiModelBounds by remember { mutableStateOf<IntRect?>(null) }

                    CustomSettingsItem(text = stringResource(R.string.settings_gemini_model)) {
                        Box {
                            Button(
                                modifier = Modifier
                                    .wrapContentSize()
                                    .width(150.dp)
                                    .onGloballyPositioned{ geminiModelBounds = it.boundsInWindow().roundToIntRect() },
                                onClick = {
                                    geminiModelDropdownVisible.targetState =
                                        !geminiModelDropdownVisible.currentState
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                )
                            ) {
                                Text(
                                    text = geminiModelText,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            if (geminiModelDropdownVisible.currentState || geminiModelDropdownVisible.targetState) {
                                CustomDropdown(
                                    transitionState = geminiModelDropdownVisible,
                                    buttons = geminiModelDropdownItems.map { (text, action) ->
                                        text to { action() }
                                    },
                                    inputBounds = geminiModelBounds,
                                    config = config,
                                    density = density,
                                    onDismissRequest = {
                                        geminiModelDropdownVisible.targetState = false
                                    },
                                    centeredArrow = true
                                )
                            }
                        }
                    }
                }
            }
            item {
                CustomSettingsItem(text = stringResource(R.string.settings_gemini_api_key)) {
                    Box {
                        Button(
                            modifier = Modifier
                                .wrapContentSize()
                                .widthIn(min = 150.dp, max = 250.dp),
                            onClick = {
                                when (geminiApiText) {
                                    defaultGeminiApiKey -> {
                                        val clipboardText: AnnotatedString? =
                                            clipboardManager.getText()

                                        clipboardText?.let {
                                            text += it.text
                                        }

                                        settingsModel.setUserGeminiApi(text)
                                        text = ""
                                    }

                                    else -> {
                                        settingsModel.setUserGeminiApi(defaultGeminiApiKey)
                                        settingsModel.setCurrentLlm("gemini-2.0-flash")
                                        settingsModel.setFilterTopics(false)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Text(
                                text = if (geminiApiText != defaultGeminiApiKey) stringResource(R.string.settings_reset) else stringResource(
                                    R.string.settings_paste
                                ),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            if (bannedNewsItems.isNotEmpty()) {
                item {
                    CustomSettingsItem(text = stringResource(R.string.settings_banned_news)) {
                        IconButton(
                            modifier = Modifier
                                .wrapContentSize(),
                            onClick = {
                                bannedNewsScreenState.targetState =
                                    !bannedNewsScreenState.currentState
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.custom_card_with_menu_icon_desc)
                            )
                        }
                    }
                }
            }

            stickyHeader() {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomTextDivider(text = stringResource(R.string.settings_chapter_additional))
                }
            }
            item {
                CustomSettingsItem(text = stringResource(R.string.settings_enable_proxy)) {
                    CustomSwitch(
                        checked = proxyEnabled,
                        onCheckedChange = { settingsModel.setProxyEnabled(it) }
                    )
                }
            }

            item {
                CustomBottomFootnote(
                    stringResource(
                        R.string.settings_footnote_text,
                        stringResource(R.string.app_version)
                    )
                )
            }
        }

        DeferredUpdateTab(
            transitionState = autoUpdateScreenState,
            onDismissRequest = {},
            items = autoUpdateItems,
            indexes = autoUpdateIndexes.value,
            header = stringResource(R.string.settings_titles_auto_update)
        )

        DeferredUpdateTab(
            transitionState = bannedNewsScreenState,
            onDismissRequest = {},
            items = bannedNewsItems,
            header = stringResource(R.string.settings_banned_news)
        )
    }
}