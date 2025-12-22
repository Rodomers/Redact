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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rds.mews.MainActivity
import com.rds.mews.R
import com.rds.mews.SettingsUiFunctions
import com.rds.mews.SettingsUiState
import com.rds.mews.viewmodels.SettingsViewModel
import com.rds.mews.localcore.intTimeToStr
import com.rds.mews.localcore.requestNotificationPermission
import com.rds.mews.ui.custom_elements.CustomBottomFootnote
import com.rds.mews.ui.custom_elements.CustomErrorBottomSheet
import com.rds.mews.ui.custom_elements.CustomIconButton
import com.rds.mews.ui.custom_elements.SettingsItem
import com.rds.mews.ui.custom_elements.CustomSwitch
import com.rds.mews.ui.custom_elements.CustomTextButton
import com.rds.mews.ui.custom_elements.LegacyTextDivider
import com.rds.mews.ui.custom_elements.DeferredUpdateTab
import com.rds.mews.ui.custom_elements.DropdownButton
import com.rds.mews.ui.theme.Shapes

@Composable
fun SettingsScreen(
    gridState: LazyGridState,
    modifier: Modifier,
    viewModel: SettingsViewModel,
    mainActivity: MainActivity
) {
    val density = LocalDensity.current

    val showDates by viewModel.showDates.collectAsStateWithLifecycle()
    val compactTab by viewModel.compactTabBar.collectAsStateWithLifecycle()
    val monetColors by viewModel.isMonetColors.collectAsStateWithLifecycle()
    val filterTopics by viewModel.filterTopics.collectAsStateWithLifecycle()
    val titlesNum by viewModel.titlesNum.collectAsStateWithLifecycle()
    val geminiApiText by viewModel.userApi.collectAsStateWithLifecycle()
    val currentLlmModel by viewModel.currentLlm.collectAsStateWithLifecycle()
    val titlesPeriod by viewModel.titlesPeriod.collectAsStateWithLifecycle()
    val rssUpdateInterval by viewModel.rssUpdateInterval.collectAsStateWithLifecycle()
    val endureTime by viewModel.endureTime.collectAsStateWithLifecycle()
    val titlesAlarmUpdate by viewModel.titlesAlarmUpdate.collectAsStateWithLifecycle()
    val alarmMins by viewModel.titlesAlarmMins.collectAsStateWithLifecycle()
    val alarmFrequency by viewModel.titlesUpdateFrequency.collectAsStateWithLifecycle()
    val bannedNews by viewModel.bannedNews.collectAsStateWithLifecycle()
    val proxyEnabled by viewModel.proxyEnabled.collectAsStateWithLifecycle()
    val showAlarmsSheet by viewModel.showAlarmsSheet.collectAsStateWithLifecycle()
    val showNotificationsSheet by viewModel.showNotificationSheet.collectAsStateWithLifecycle()
    val isApiKeyDefault by viewModel.isKeyDefault.collectAsStateWithLifecycle()
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

    val state = SettingsUiState(
        showDates = showDates,
        compactTab = compactTab,
        currentTheme = currentTheme,
        monetColors = monetColors,
        filterTopics = filterTopics,
        titlesNum = titlesNum,
        geminiApiText = geminiApiText,
        currentLlmModel = currentLlmModel,
        titlesPeriod = titlesPeriod,
        rssUpdateInterval = rssUpdateInterval,
        endureTime = endureTime,
        titlesAlarmUpdate = titlesAlarmUpdate,
        alarmMins = alarmMins,
        alarmFrequency = alarmFrequency,
        bannedNews = bannedNews,
        proxyEnabled = proxyEnabled,
        showAlarmsSheet = showAlarmsSheet,
        showNotificationsSheet = showNotificationsSheet,
        defaultApiCheck = isApiKeyDefault
    )

    val functions = remember {
        SettingsUiFunctions(
            setCompactTab = viewModel::setCompactTab,
            setMonetColors = viewModel::setMonetColors,
            setCurrentTheme = viewModel::setCurrentTheme,
            setShowDates = viewModel::setShowDates,
            setEndureTime = viewModel::setEndureTime,
            setTitlesNum = viewModel::setTitlesNum,
            setTitlesPeriod = viewModel::setTitlesPeriod,
            setRssUpdateInterval = viewModel::setRssUpdateInterval,
            setFilterTopics = viewModel::setFilterTopics,
            setBannedNews = viewModel::setBannedNews,
            delBannedNews = viewModel::delBannedNews,
            setCurrentLlm = viewModel::setCurrentLlm,
            setUserGeminiApi = viewModel::setUserGeminiApi,
            resetUserGeminiApi = viewModel::resetApiKey,
            setProxyEnabled = viewModel::setProxyEnabled,
            setTitlesAlarmUpdate = viewModel::setTitlesAlarmUpdate,
            setTitlesAlarmMins = viewModel::setTitlesAlarmMins,
            setTitlesUpdFrequency = viewModel::setTitlesUpdFrequency,
            setAlarmsAllowed = viewModel::setAlarmsAllowed,
            planTitlesAutoUpdate = viewModel::planTitlesAutoUpdate,
            setShowAlarmsSheet = viewModel::setShowAlarmsSheet,
            setShowNotificationsSheet = viewModel::setShowNotificationsSheet,
        )
    }

    SettingsGrid(
        gridState = gridState,
        modifier = modifier,
        mainActivity = mainActivity,
        density = density,
        state = state,
        functions = functions
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGrid(
    gridState: LazyGridState,
    modifier: Modifier,
    mainActivity: MainActivity,
    density: Density,
    state: SettingsUiState,
    functions: SettingsUiFunctions
) {
    val clipboardManager = LocalClipboardManager.current

    var text by remember { mutableStateOf("") }
    var alarmHrsText by remember { mutableIntStateOf(state.alarmMins / 60) }
    var alarmMinsText by remember { mutableIntStateOf(state.alarmMins % 60) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.alarmMins) {
        alarmHrsText = state.alarmMins / 60
        alarmMinsText = state.alarmMins % 60
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val colorSchemeDropdownItems = mutableListOf(
        stringResource(R.string.settings_system_theme) to { functions.setCurrentTheme("system") },
        stringResource(R.string.settings_light_theme) to { functions.setCurrentTheme("light") },
        stringResource(R.string.settings_dark_theme) to { functions.setCurrentTheme("dark") }
    )

    val titlesDropdownItems = mutableListOf(
        pluralStringResource(R.plurals.titles, count = 10, 10) to { functions.setTitlesNum(10) },
        pluralStringResource(R.plurals.titles, count = 20, 20) to { functions.setTitlesNum(20) },
    )
    if (!state.defaultApiCheck) {
        titlesDropdownItems.addAll(listOf(
            pluralStringResource(R.plurals.titles, count = 30, 30) to { functions.setTitlesNum(30) },
            pluralStringResource(R.plurals.titles, count = 40, 40) to { functions.setTitlesNum(40) },
            pluralStringResource(R.plurals.titles, count = 50, 50) to { functions.setTitlesNum(50) })
        )
    }
    else if (state.titlesNum > 20) functions.setTitlesNum(20)

    val limitationDropdownItems = listOf(
        pluralStringResource(R.plurals.hours, count = 12, 12) to { functions.setTitlesPeriod(12) },
        pluralStringResource(R.plurals.hours, count = 24, 24) to { functions.setTitlesPeriod(24) },
        pluralStringResource(R.plurals.hours, count = 48, 48) to { functions.setTitlesPeriod(48) },
        pluralStringResource(R.plurals.hours, count = 72, 72) to { functions.setTitlesPeriod(72) },
        pluralStringResource(R.plurals.hours, count = 96, 96) to { functions.setTitlesPeriod(96) },
        pluralStringResource(R.plurals.hours, count = 120, 120) to { functions.setTitlesPeriod(120) }
    )

    val rssUpdateDropdownItems = listOf(
        pluralStringResource(R.plurals.minutes, count = 15, 15) to { functions.setRssUpdateInterval(context, 15) },
        pluralStringResource(R.plurals.minutes, count = 30, 30) to { functions.setRssUpdateInterval(context, 30) },
        pluralStringResource(R.plurals.minutes, count = 60, 60) to { functions.setRssUpdateInterval(context, 60) }
    )

    val geminiModelDropdownItems = listOf(
        "2.5 Flash" to { functions.setCurrentLlm("gemini-2.5-flash") },
        "2.5 Flash Lite" to { functions.setCurrentLlm("gemini-2.5-flash-lite") },
        "2.0 Flash" to { functions.setCurrentLlm("gemini-2.0-flash") },
        "2.0 Flash Lite" to { functions.setCurrentLlm("gemini-2.0-flash-lite") }
    )

    val alarmHrsItems = (0..23).toList().map { num ->
        intTimeToStr(num) to {
            alarmHrsText = num
            functions.setTitlesAlarmMins(context, alarmHrsText * 60 + alarmMinsText)
        }
    }
    val alarmMinsItems = (0..59).toList().map {num ->
        intTimeToStr(num) to {
            alarmMinsText = num
            functions.setTitlesAlarmMins(context, alarmHrsText * 60 + alarmMinsText)
        }
    }

    val titlesFrequencyItems = listOf (
        pluralStringResource(R.plurals.hours, count = 12, 12) to { functions.setTitlesUpdFrequency(context, 12) },
        pluralStringResource(R.plurals.hours, count = 24, 24) to { functions.setTitlesUpdFrequency(context, 24) },
        pluralStringResource(R.plurals.hours, count = 48, 48) to { functions.setTitlesUpdFrequency(context, 48) },
        pluralStringResource(R.plurals.hours, count = 72, 72) to { functions.setTitlesUpdFrequency(context, 72) },
        pluralStringResource(R.plurals.hours, count = 96, 96) to { functions.setTitlesUpdFrequency(context, 96) },
        pluralStringResource(R.plurals.hours, count = 120, 120) to { functions.setTitlesUpdFrequency(context, 120) }
    )

    val autoUpdateScreenState = remember { MutableTransitionState(false) }
    val autoUpdateIndexes = remember { mutableStateOf<List<Int>?>(listOf(0)) }
    LaunchedEffect(state.titlesAlarmUpdate) {
        autoUpdateIndexes.value = if (state.titlesAlarmUpdate) null else listOf(0)
    }
    val autoUpdateItems: List<@Composable () -> Unit> = listOf(
        {
            Spacer(modifier = Modifier.height(12.dp))

            SettingsItem(text = stringResource(R.string.settings_titles_alarm_update)) {
                CustomSwitch(
                    checked = state.titlesAlarmUpdate,
                    onCheckedChange = {
                        functions.setTitlesAlarmUpdate(
                            context,
                            { functions.setShowNotificationsSheet(true) },
                            { functions.setShowAlarmsSheet(true) },
                            it,
                            mainActivity
                        )
                    }
                )
            }
        },
        {
            SettingsItem(text = stringResource(R.string.settings_titles_update_frequency)) {
                DropdownButton(
                    buttons = titlesFrequencyItems.map { (text, action) ->
                        text to { action() }
                    },
                    density = density,
                    cornerShape = Shapes.large,
                    initialSelectedIndex = titlesFrequencyItems.indexOfFirst { it.first.contains(state.alarmFrequency.toString()) },
                    width = 160.dp
                )
            }
        },
        {
            SettingsItem(text = stringResource(R.string.settings_titles_update_time)) {
                Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DropdownButton(
                            buttons = alarmHrsItems.mapNotNull { (text, action) ->
                                if (!(state.alarmFrequency == 12 && text.toInt() > 12)) text to { action() } else null
                            },
                            density = density,
                            cornerShape = Shapes.large,
                            width = 75.dp,
                            initialSelectedIndex = alarmHrsItems.mapNotNull { (text, action) ->
                                if (!(state.alarmFrequency == 12 && text.toInt() > 12)) text to { action() } else null
                            }.indexOfFirst { it.first.contains(alarmHrsText.toString()) }
                        )
                        Text(
                            text = ":",
                            fontSize = 22.sp,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        DropdownButton(
                            buttons = alarmMinsItems.map { (text, action) ->
                                text to { action() }
                            },
                            density = density,
                            cornerShape = Shapes.large,
                            width = 75.dp,
                            initialSelectedIndex = alarmMinsItems.indexOfFirst { it.first.contains(alarmMinsText.toString()) }
                        )
                    }
                }
            }
        }
    )

    val bannedNewsScreenState = remember { MutableTransitionState(false) }
    val bannedNewsItems: MutableList<@Composable () -> Unit> = mutableListOf()
    state.bannedNews.forEach {
        if (it != ""){
            bannedNewsItems.add(
                {
                    SettingsItem(it) {
                        CustomIconButton(
                            icon = Icons.Default.Close,
                            onClick = { functions.delBannedNews(it) },
                            modifier = Modifier
                                .padding(8.dp)
                                .size(16.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        )
                    }
                }
            )
        }
    }
    LaunchedEffect(bannedNewsItems) {
        if (bannedNewsItems.isEmpty()) bannedNewsScreenState.targetState = false
    }

    if (state.showAlarmsSheet) {
        CustomErrorBottomSheet(
            title = stringResource(R.string.settings_alarms_sheet_title),
            text = stringResource(R.string.settings_alarms_sheet_text),
            confBtnText = stringResource(R.string.settings_alarms_sheet_conf),
            cancelBtnText = stringResource(R.string.settings_alarms_sheet_cancel),
            onDismissRequest = { functions.setShowAlarmsSheet(false) },
            onConfirm = {
                functions.setShowAlarmsSheet(false)
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

    if (state.showNotificationsSheet) {
        CustomErrorBottomSheet(
            title = stringResource(R.string.settings_notification_sheet_title),
            text = stringResource(R.string.settings_notification_sheet_text),
            confBtnText = stringResource(R.string.settings_notification_sheet_conf),
            cancelBtnText = stringResource(R.string.settings_notification_sheet_cancel),
            onDismissRequest = { functions.setShowNotificationsSheet(false) },
            onConfirm = {
                functions.setShowNotificationsSheet(false)
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
                    LegacyTextDivider(text = stringResource(R.string.settings_chapter_appearance))
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_compact_tab)) {
                    CustomSwitch(
                        checked = state.compactTab,
                        onCheckedChange = { functions.setCompactTab(it) }
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SettingsItem(text = stringResource(R.string.settings_monet_colors)) {
                        CustomSwitch(
                            checked = state.monetColors,
                            onCheckedChange = { functions.setMonetColors(it) }
                        )
                    }
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_color_scheme)) {
                    DropdownButton(
                        buttons = colorSchemeDropdownItems.map { (text, action) ->
                            text to { action() }
                        },
                        density = density,
                        cornerShape = Shapes.large,
                        initialSelectedIndex = when (state.currentTheme) {
                            "light" -> 1
                            "dark" -> 2
                            else -> 0
                        }
                    )
                }
            }

            stickyHeader() {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LegacyTextDivider(text = stringResource(R.string.settings_chapter_titles))
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_show_dates)) {
                    CustomSwitch(
                        checked = state.showDates,
                        onCheckedChange = { functions.setShowDates(it) }
                    )
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_endure_time)) {
                    CustomSwitch(
                        checked = state.endureTime,
                        onCheckedChange = { functions.setEndureTime(it) }
                    )
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_maximum_headers)) {
                    DropdownButton(
                        buttons = titlesDropdownItems.map { (text, action) ->
                            text to { action() }
                        },
                        density = density,
                        cornerShape = Shapes.large,
                        initialSelectedIndex = titlesDropdownItems.indexOfFirst { it.first.contains(state.titlesNum.toString()) }
                    )
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_news_period)) {
                    DropdownButton(
                        buttons = limitationDropdownItems.map { (text, action) ->
                            text to { action() }
                        },
                        density = density,
                        cornerShape = Shapes.large,
                        initialSelectedIndex = limitationDropdownItems.indexOfFirst { it.first.contains(state.titlesPeriod.toString()) }
                    )
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_news_update_frequency)) {
                    DropdownButton(
                        buttons = rssUpdateDropdownItems,
                        density = density,
                        cornerShape = Shapes.large,
                        initialSelectedIndex = rssUpdateDropdownItems.indexOfFirst { it.first.contains(state.rssUpdateInterval.toString()) }
                    )
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_titles_auto_update)) {
                    CustomIconButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = {
                            autoUpdateScreenState.targetState = !autoUpdateScreenState.currentState
                        },
                        modifier = Modifier
                            .wrapContentSize()
                            .height(40.dp)
                    )
                }
            }

            stickyHeader() {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LegacyTextDivider(text = stringResource(R.string.settings_chapter_llm))
                }
            }
            if (!state.defaultApiCheck) {
                item {
                    SettingsItem(text = stringResource(R.string.settings_filter_topics)) {
                        CustomSwitch(
                            checked = state.filterTopics,
                            onCheckedChange = { functions.setFilterTopics(it) }
                        )
                    }
                }
            }
            if (!state.defaultApiCheck) {
                item {
                    SettingsItem(text = stringResource(R.string.settings_gemini_model)) {
                        DropdownButton(
                            buttons = geminiModelDropdownItems.map { (text, action) ->
                                text to { action() }
                            },
                            density = density,
                            cornerShape = Shapes.large,
                            initialSelectedIndex = geminiModelDropdownItems.indexOfFirst {
                                "gemini-${
                                    it.first.split(" ")
                                        .joinToString("-") { item -> item.lowercase() }
                                }" == state.currentLlmModel
                            }
                        )
                    }
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_gemini_api_key)) {
                    CustomTextButton(
                        text = if (!state.defaultApiCheck) stringResource(R.string.settings_reset) else stringResource(
                            R.string.settings_paste
                        ),
                        onClick = {
                            when (state.defaultApiCheck) {
                                true -> {
                                    val clipboardText: AnnotatedString? =
                                        clipboardManager.getText()

                                    clipboardText?.let {
                                        text += it.text
                                    }

                                    functions.setUserGeminiApi(text)
                                    text = ""
                                }

                                else -> {
                                    functions.resetUserGeminiApi()
                                    functions.setCurrentLlm("gemini-2.0-flash")
                                    functions.setFilterTopics(false)
                                }
                            }
                        },
                        modifier = Modifier
                            .wrapContentSize()
                            .widthIn(min = 150.dp, max = 250.dp),
                        shape = Shapes.large,
                        defaultBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha=0.98f)
                    )
                }
            }
            if (bannedNewsItems.isNotEmpty()) {
                item {
                    SettingsItem(text = stringResource(R.string.settings_banned_news)) {
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
                    LegacyTextDivider(text = stringResource(R.string.settings_chapter_additional))
                }
            }
            item {
                SettingsItem(text = stringResource(R.string.settings_enable_proxy)) {
                    CustomSwitch(
                        checked = state.proxyEnabled,
                        onCheckedChange = { functions.setProxyEnabled(it) }
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