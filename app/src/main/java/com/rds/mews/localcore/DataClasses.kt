package com.rds.mews.localcore

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
import com.rds.mews.MainActivity

data class Message(var id: Long, var time: Long, var link: String, var source: String, var mess: String)
data class RSS(var id: Long, var source: String, var link: String)
data class Title(var id: Long, var time: Long, var title: String, var text: String, var sources: String, var ids: String)
data class TitleCardStates(var id: Long, var expanded: Boolean = false, var currentPage: Int = 0, var sources: List<SourceMessages>? = null)

enum class SummarizationErrorType {
    EXTRACT_TOPICS_FAILED,
    SUMMARIZE_TOPICS_FAILED,
    CRITICAL_SUMMARIZATION_ERROR,
    JSON_PARSING_FAILED,
    NETWORK_TIMEOUT,
    EMPTY_ANSWER,
    NO_NEWS_TO_ANALYZE,
    FILTER_FAILED,
    JOB_CANCELLED,
    RATE_LIMIT_EXCEEDED,
    NO_NETWORK,
    UNKNOWN_ERROR
}

enum class SourceType {
    RSS_FEED,
    TELEGRAM_CHANNEL
}

enum class ArrowPosition {
    TopLeft, TopCenter, TopRight,
    BottomLeft, BottomCenter, BottomRight,
    LeftTop, LeftCenter, LeftBottom,
    RightTop, RightCenter, RightBottom,
    None
}

enum class ScreenQuadrant {
    TopLeft, TopRight, BottomLeft, BottomRight
}

sealed interface SummarizationResult {
    data object Success : SummarizationResult
    data class Failure(
        val type: SummarizationErrorType,
        val cause: Throwable? = null
    ) : SummarizationResult
}

data class SettingsUiState(
    val showDates: Boolean,
    val compactTab: Boolean,
    val currentTheme: String,
    val monetColors: Boolean,
    val filterTopics: Boolean,
    val titlesNum: Int,
    val geminiApiText: String,
    val currentLlmModel: String,
    val titlesPeriod: Int,
    val rssUpdateInterval: Int,
    val endureTime: Boolean,
    val titlesAlarmUpdate: Boolean,
    val alarmMins: Int,
    val alarmFrequency: Int,
    val bannedNews: Set<String>,
    val proxyEnabled: Boolean,
    val showAlarmsSheet: Boolean,
    val showNotificationsSheet: Boolean,
    val defaultApiCheck: Boolean
)

data class SettingsUiFunctions(
    val setCompactTab: (Boolean) -> Unit,
    val setMonetColors: (Boolean) -> Unit,
    val setCurrentTheme: (String) -> Unit,
    val setShowDates: (Boolean) -> Unit,
    val setEndureTime: (Boolean) -> Unit,
    val setTitlesNum: (Int) -> Unit,
    val setTitlesPeriod: (Int) -> Unit,
    val setRssUpdateInterval: (Context, Int) -> Unit,
    val setFilterTopics: (Boolean) -> Unit,
    val setBannedNews: (Set<String>) -> Unit,
    val delBannedNews: (String) -> Unit,
    val setCurrentLlm: (String) -> Unit,
    val setUserGeminiApi: (String) -> Unit,
    val resetUserGeminiApi: () -> Unit,
    val setProxyEnabled: (Boolean) -> Unit,
    val setTitlesAlarmUpdate: (Context, () -> Unit, () -> Unit, Boolean, MainActivity) -> Unit,
    val setTitlesAlarmMins: (Context, Int) -> Unit,
    val setTitlesUpdFrequency: (Context, Int) -> Unit,
    val setAlarmsAllowed: (Boolean) -> Unit,
    val planTitlesAutoUpdate: (Context) -> Unit,
    val setShowAlarmsSheet: (Boolean) -> Unit,
    val setShowNotificationsSheet: (Boolean) -> Unit,
    val addGroupState: (Int, Boolean) -> Unit,
    val changeGroupState: (Int) -> Unit
)

data class SourcesGroupState(val group: SourceType, val expanded: Boolean)
data class TitlesGroupState(val group: TimeDate, val expanded: Boolean)
data class SettingsGroupState(val group: Int, val expanded: Boolean)

data class SourceMessages(val source: String, val state: Boolean, val messages: List<Message>)
data class TimeDate(val number: Int? = null, val date: Int, val time: String)

data class TextButtonInputs(
    val text: String,
    override val action: () -> Unit,
    override val toast: String? = null
) : ButtonInputs
data class IconButtonInputs(
    val icon: ImageVector,
    override val action: () -> Unit,
    override val toast: String? = null
) : ButtonInputs
