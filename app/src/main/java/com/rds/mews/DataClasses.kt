package com.rds.mews

data class Message(var id: Long, var time: Long, var link: String, var source: String, var mess: String)
data class RSS(var id: Long, var source: String, var link: String)
data class Title(var id: Long, var time: Long, var title: String, var text: String, var sources: String, var links: String)
data class TitleCardStates(var id: Long, var expanded: Boolean = false, var currentPage: Int = 0)

enum class SummarizationErrorType {
    EXTRACT_TOPICS_FAILED,
    SUMMARIZE_TOPICS_FAILED,
    CRITICAL_SUMMARIZATION_ERROR,
    JSON_PARSING_FAILED,
    NETWORK_TIMEOUT,
    NO_NEWS_TO_ANALYZE,
    FILTER_FAILED,
    UNKNOWN_ERROR
}

enum class SourceType {
    RSS_FEED,
    TELEGRAM_CHANNEL
}

sealed interface SummarizationResult {
    data object Success : SummarizationResult
    data class Failure(
        val type: SummarizationErrorType,
        val cause: Throwable? = null
    ) : SummarizationResult
}