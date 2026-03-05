package com.rds.mews.settings_manager

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import androidx.datastore.core.Serializer
import com.rds.mews.localcore.AppTheme
import com.rds.mews.localcore.AutoUpdateFrequency
import com.rds.mews.localcore.DarkTheme
import com.rds.mews.localcore.GeminiModelOption
import com.rds.mews.localcore.HeadersNum
import com.rds.mews.localcore.TitlesPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

@Serializable
enum class SummarizationErrorType {
    EXTRACT_TOPICS_FAILED,
    SUMMARIZE_TOPICS_FAILED,
    JSON_PARSING_FAILED,
    NETWORK_TIMEOUT,
    EMPTY_ANSWER,
    NO_NEWS_TO_ANALYZE,
    UNPROCESSED_ITEMS,
    FILTER_FAILED,
    JOB_CANCELLED,
    RATE_LIMIT_EXCEEDED,
    NO_NETWORK,
    API_KEY_INVALID,
    QUOTA_EXCEEDED,
    CONTENT_BLOCKED,
    UNKNOWN_ERROR
}

class GeminiException(val errorType: SummarizationErrorType, message: String? = null) : Exception(message)

@Serializable
data class SavedError(
    val type: SummarizationErrorType,
    val message: String
)

@Serializable
data class AppSettings(
    // Theme
    val darkTheme: DarkTheme = DarkTheme.SYSTEM,
    val appTheme: AppTheme = AppTheme.DEFAULT,

    // Content
    val titlesNum: HeadersNum = HeadersNum.NUM_10,
    val titlesPeriod: TitlesPeriod = TitlesPeriod.ADAPTIVE,

    // Network & AI
    val userApiKey: String = "",
    val llmModel: GeminiModelOption = GeminiModelOption.FLASH_LITE_LATEST,
    val enableProxy: Boolean = false,

    // Updates
    val titlesAutoUpdate: Boolean = false,
    val titlesAutoUpdateFrequency: AutoUpdateFrequency = AutoUpdateFrequency.FREQ_24,
    val titlesAlarmTimeMins: Int = 540,
    val alarmsAllowed: Boolean = false,
    val notificationsGranted: Boolean = false,
    val rssUpdateInterval: Int = 30,
    val lastRssUpdate: Long = 0L,

    // UI Toggles
    val compactTabBar: Boolean = false,
    val filterTopics: Boolean = false,
    val innerTimestamps: Boolean = false,
    val showSnippets: Boolean = false,
    val showDates: Boolean = false,
    val currentLanguage: String = "English",

    // Technical State
//    val updatingTitles: Boolean = false,
    val updatingState: String = "off",
    val updatingProgress: Float = 0f,
    val lastTitlesUpdate: Long = 0L,
    val bannedNews: Set<String> = emptySet(),
    val lastError: SavedError? = null
)

object AppSettingsSerializer : Serializer<AppSettings> {
    override val defaultValue: AppSettings = AppSettings()

    override suspend fun readFrom(input: InputStream): AppSettings {
        return try {
            Json.decodeFromString(AppSettings.serializer(), input.readBytes().decodeToString())
        } catch (exception: SerializationException) {
            exception.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(Json.encodeToString(AppSettings.serializer(), t).encodeToByteArray())
        }
    }
}