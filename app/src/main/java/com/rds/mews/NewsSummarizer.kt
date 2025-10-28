package com.rds.mews

import kotlin.collections.mutableListOf
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.pow


private data class SummaryResult(
    val originalTitle: String,
    val summary: String,
    val time: Long,
    val sources: List<String>,
    val links: List<String>
)

@Serializable
private data class TopicExtractionResponse(
    val title: String,
    val id: List<Long>
)

@Serializable
private data class SummarizationResponse(
    val title: String,
    val summary: String
)

private class RateLimitException(message: String) : Exception(message)

class LLMClient(
    val apiKey: String,
    val MODEL: String = "gemini-2.0-flash-lite",
    private val URL: String = "https://generativelanguage.googleapis.com/v1beta/models/${if (MODEL != "") MODEL else "gemini-2.0-flash"}:generateContent"
) {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
        }
    }

    suspend fun sendPromptWithRetries(
        prompt: String,
        totalTimeoutMillis: Long = 90000L,
        maxRetries: Int = 3,
        initialDelayMillis: Long = 4000L
    ): String? {
        return try {
            withTimeout(totalTimeoutMillis) {
                var currentAttempt = 0
                while (currentAttempt < maxRetries) {
                    try {
                        return@withTimeout sendSinglePrompt(prompt)
                    } catch (e: ClientRequestException) {
                        if (e.response.status == HttpStatusCode.TooManyRequests) {
                            throw RateLimitException("API rate limit exceeded. Status: 429.")
                        }
                        currentAttempt++
                        if (currentAttempt >= maxRetries) throw e
                        val delayTime = initialDelayMillis * (2.0.pow(currentAttempt - 1).toLong())
                        println("Попытка $currentAttempt/$maxRetries не удалась: ${e.message}. Повтор через ${delayTime / 1000}с...")
                        delay(delayTime)

                    } catch (e: Exception) {
                        currentAttempt++
                        if (currentAttempt >= maxRetries) throw e
                        val delayTime = initialDelayMillis * (2.0.pow(currentAttempt - 1).toLong())
                        println("Попытка $currentAttempt/$maxRetries не удалась: ${e.message}. Повтор через ${delayTime / 1000}с...")
                        delay(delayTime)
                    }
                }
                null
            }
        } catch (e: Exception) {
            throw e
        }
    }

    private suspend fun sendSinglePrompt(prompt: String): String? {
        val requestBody = GeminiRequest(
            contents = listOf(ContentInput(parts = listOf(PartInput(prompt)))),
            generationConfig = GenerationConfig(temperature = 0.5, maxOutputTokens = 8192)
        )

        val responseString: String = httpClient.post(URL) {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body()

        val json = Json { ignoreUnknownKeys = true }
        val geminiResponse = json.decodeFromString<GeminiResponse>(responseString)

        return geminiResponse.candidates?.takeIf { it.isNotEmpty() }?.let {
            it.flatMap { candidate -> candidate.content.parts }.joinToString("\n") { part -> part.text }
        }
    }

    fun close() {
        httpClient.close()
    }

    @Serializable
    data class GeminiRequest(
        val contents: List<ContentInput>,
        val generationConfig: GenerationConfig? = null
    )

    @Serializable
    data class GenerationConfig(
        val temperature: Double,
        val maxOutputTokens: Int? = null
    )

    @Serializable
    data class ContentInput(
        val parts: List<PartInput>
    )

    @Serializable
    data class PartInput(
        val text: String
    )

    @Serializable
    data class GeminiResponse(
        val candidates: List<Candidate>?
    )

    @Serializable
    data class Candidate(
        val content: Content
    )

    @Serializable
    data class Content(
        val parts: List<Part>
    )

    @Serializable
    data class Part(
        val text: String
    )
}

class NewsSummarizer(
    private val db: DbHelper,
    private val llm: LLMClient
) {
    data class Topics(
        val title: String,
        val ids: List<Long>?
    )

    private data class SummaryResult(
        val originalTitle: String,
        val summary: String,
        val time: Long,
        val sources: List<String>,
        val links: List<String>
    )

    private val _newsBatchSize = 70
    private val _interRequestDelay = 2000L

    suspend fun summarizeTopics(
        maxTopics: Int = 20,
        messageSeconds: Long = 14515200,
        readyFunc: () -> Unit,
        settingsManager: SettingsManager,
        filterTopics: Boolean = false
    ): SummarizationResult {
        try {
            val bannedNews = "Таких тем нет"

            val messages = db.getMessages(messageSeconds).sortedBy { it.time }
            if (messages.isEmpty()) {
                readyFunc()
                return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE)
            }

            val unfinishedTitles = db.getTitles().filter {
                it.text == "<промежуточный текст>" && it.time.toInt() == 0
            }

            if (unfinishedTitles.isEmpty()) {
                settingsManager.saveString(MewsRepository.UPDATING_STATE, "extracting_topics")
                val currentLanguage = settingsManager.getString(MewsRepository.CURRENT_LANGUAGE, "russian")

                val success = processNewsInBatches(maxTopics, messages, currentLanguage, filterTopics, bannedNews)
                if (!success) {
                    readyFunc()
                    return SummarizationResult.Failure(SummarizationErrorType.EXTRACT_TOPICS_FAILED)
                }
                settingsManager.saveLong(MewsRepository.LAST_TITLES_UPDATE, System.currentTimeMillis())
            }

            val titlesToSummarize = db.getTitles().filter {
                it.text == "<промежуточный текст>"
            }.map {
                Topics(it.title, db.dbUnpack(it.links).mapNotNull { id -> id.toLongOrNull() })
            }

            if (titlesToSummarize.isEmpty()) {
                readyFunc()
                return SummarizationResult.Success
            }

            val semaphore = Semaphore(3)
            val successfulSummaries = mutableListOf<SummaryResult>()
            val failedTasksExceptions = mutableListOf<Throwable>()

            coroutineScope {
                val deferredJobs = titlesToSummarize.mapIndexed { index, title ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            if (index > 0) delay(_interRequestDelay)

                            settingsManager.saveString(MewsRepository.UPDATING_STATE, "${index + 1}/${titlesToSummarize.size}")
                            val currentLanguage = settingsManager.getString(MewsRepository.CURRENT_LANGUAGE, "russian")

                            val suitableMessages = title.ids?.mapNotNull { id -> messages.find { it.id == id } } ?: emptyList()
                            if (suitableMessages.isEmpty()) return@withPermit null

                            val newsText = suitableMessages.joinToString("\n") { "— ${it.mess}" }

                            val summaryResponse = sumTopic(llm, title.title, bannedNews, newsText, currentLanguage)

                            if (summaryResponse == null) {
                                throw IllegalStateException("Received empty answer from LLM for topic: ${title.title}")
                            }

                            val time = suitableMessages.minOfOrNull { it.time } ?: System.currentTimeMillis()
                            val links = suitableMessages.map { it.link }.distinct()
                            val sources = suitableMessages.map { it.source }.distinct()

                            SummaryResult(title.title, summaryResponse.summary, time, sources, links)
                        }
                    }
                }

                deferredJobs.forEach { deferred ->
                    try {
                        deferred.await()?.let { successfulSummaries.add(it) }
                    } catch (e: Exception) {
                        failedTasksExceptions.add(e)
                        e.printStackTrace()
                    }
                }
            }

            if (successfulSummaries.isNotEmpty()) {
                successfulSummaries.forEach { result ->
                    db.updateTitle(
                        name = result.originalTitle,
                        newTime = result.time,
                        newText = result.summary,
                        newSources = db.dbPack(*result.sources.toTypedArray()),
                        newLinks = db.dbPack(*result.links.toTypedArray())
                    )
                }
            }

            if (failedTasksExceptions.isNotEmpty()) {
                val firstError = failedTasksExceptions.first()
                val errorType = when (firstError) {
                    is SerializationException -> SummarizationErrorType.JSON_PARSING_FAILED
                    is TimeoutCancellationException -> SummarizationErrorType.NETWORK_TIMEOUT
                    is CancellationException -> SummarizationErrorType.JOB_CANCELLED
                    is IllegalStateException -> SummarizationErrorType.EMPTY_ANSWER
                    is RateLimitException -> SummarizationErrorType.RATE_LIMIT_EXCEEDED
                    else -> SummarizationErrorType.SUMMARIZE_TOPICS_FAILED
                }
                return SummarizationResult.Failure(errorType, firstError)
            }

            readyFunc()
            return SummarizationResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            readyFunc()
            return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
        }
    }

    private suspend fun processNewsInBatches(maxTopics: Int, messages: List<Message>, currentLanguage: String, filterTopics: Boolean = false, bannedNews: String): Boolean {
        return try {
            val messageBatches = messages.chunked(_newsBatchSize)
            val allExtractedTopics = coroutineScope {
                messageBatches.map { batch ->
                    async(Dispatchers.IO) {
                        extractTopicsFromBatch(batch, maxTopics, currentLanguage, bannedNews)
                    }
                }.mapNotNull { it.await() }.flatten()
            }

            if (allExtractedTopics.isEmpty()) {
                println("Не удалось извлечь ни одной темы из новостей.")
                false
            }

            if (filterTopics) mergeAndFilterTopics(allExtractedTopics, maxTopics, currentLanguage, bannedNews) else true
        } catch (e: Exception) {
            println("Критическая ошибка на этапе извлечения тем: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private suspend fun extractTopicsFromBatch(messagesBatch: List<Message>, max: Int, currentLanguage: String, bannedNews: String): List<Topics>? {
        val prompt = createExtractionPrompt(messagesBatch, max, currentLanguage, bannedNews)

        val response = llm.sendPromptWithRetries(prompt) ?: return null
        val cleanResponse = response.trim().removePrefix("```json").removeSuffix("```").trim()

        val topicsResponse = Json.decodeFromString<List<TopicExtractionResponse>>(cleanResponse)
        return topicsResponse.map { Topics(it.title, it.id) }
    }

    private suspend fun mergeAndFilterTopics(topics: List<Topics>, maxTopics: Int, currentLanguage: String, bannedNews: String): Boolean {
        val prompt = createMergePrompt(topics, maxTopics, currentLanguage, bannedNews)

        val response = llm.sendPromptWithRetries(prompt) ?: return false
        val cleanResponse = response.trim().removePrefix("```json").removeSuffix("```").trim()

        val finalTopics = Json.decodeFromString<List<TopicExtractionResponse>>(cleanResponse)
        val iterEnd = min(finalTopics.size, maxTopics)

        db.titlesTimeKill(0)

        for (i in 0 until iterEnd) {
            val topic = finalTopics[i]
            db.addTitle(
                titleTime = 0,
                title = topic.title,
                text = "<промежуточный текст>",
                sources = "<промежуточный текст>",
                links = db.dbPack(*topic.id.map { it.toString() }.toTypedArray())
            )
        }
        return true
    }

    private suspend fun sumTopic(llm: LLMClient, title: String, bannedNews: String, newsText: String, currentLanguage: String = "russian"): SummarizationResponse? {
        val prompt = createSummaryPrompt(title, bannedNews, newsText, currentLanguage)

        val response = llm.sendPromptWithRetries(prompt) ?: return null
        val cleanResponse = response.trim().removePrefix("```json").removeSuffix("```").trim()

        return Json.decodeFromString<SummarizationResponse>(cleanResponse)
    }

    private fun createExtractionPrompt(messagesBatch: List<Message>, max: Int, currentLanguage: String, bannedNews: String): String {
        val combinedNews = messagesBatch.joinToString("\n") { "• ${it.mess} (id - ${it.id})" }

        return """
    Ты — опытный новостной редактор. Твоя задача — проанализировать список новостей и сгруппировать их по ключевым событиям.

    Инструкции:
    1.  **Выдели от 1 до $max главных событий.** Группируй новости, описывающие одно и то же событие или тесно связанные происшествия.
    2.  **Сортируй темы по важности.** Важность определяется влиянием события на большое количество людей, его масштабом и значимостью.
    3.  **Формулируй заголовки тем.** Каждый заголовок должен быть информативным и состоять из 5-7 слов.
    4.  **Обработка мелких новостей:** Новости, которые не относятся ни к одному крупному событию (мелкие происшествия, локальные анонсы), сгруппируй в одну общую тему под названием "Другие новости и короткие события". Эта тема должна содержать примерно столько же новостей, сколько и другие важные темы, и быть одной из многих, а не собирать всё подряд.
    5.  **Уникальность:** Каждая новость (id) может относиться только к ОДНОЙ теме. Если новость подходит к двум темам, выбери наиболее подходящую.
    6.  **Запрещённые темы:** Игнорируй и не включай в анализ новости, связанные с темами: "$bannedNews".
    7.  **Контекст:** Текущее время (Unix millis) - ${System.currentTimeMillis()}.

    Формат ответа:
    -   Верни ответ СТРОГО в виде JSON-массива. Никакого текста до или после.
    -   Язык заголовков: ${currentLanguage}.

    Пример: [{"title": "Введены новые экономические санкции против банков", "id": [101, 105]}, {"title": "Запуск новой линии метро в столице", "id": [102, 108]}, {"title": "Другие новости и короткие события", "id": [103, 104, 109]}]

    Новости для анализа:
    $combinedNews
""".trimIndent()
    }

    private fun createMergePrompt(topics: List<Topics>, maxTopics: Int, currentLanguage: String, bannedNews: String): String {
        val titlesJsonForPrompt = Json.encodeToString(
            topics.map { TopicExtractionResponse(it.title, it.ids ?: emptyList()) }
        )
        return """
    Ты — главный редактор, твоя задача — проанализировать предложенный список новостных тем, объединить дубликаты, отфильтровать маловажные и привести заголовки к единому стандарту.

    Инструкции:
    1.  **Объедини схожие темы.** Например, "Атаки дронов на РФ" и "Налеты БПЛА на регионы России" должны стать одной темой. При объединении сохрани все уникальные ID сообщений из обеих тем. При выборе итогового названия отдавай предпочтение более полному и информативному варианту.
    2.  **Удали темы, связанные с запрещёнными категориями:** $bannedNews.
    3.  **Отфильтруй и оставь ровно $maxTopics самых важных тем.** Если тем изначально меньше, оставь все. Приоритет отдавай темам с наибольшим количеством новостей и наибольшим общественным резонансом.
    4.  **Отформатируй заголовки:** Убедись, что все итоговые заголовки состоят из 5-7 слов, являются ёмкими и точно отражают суть события.
    5.  **Отсортируй финальный список** по убыванию важности.

    Формат ответа:
    -   Верни результат в виде JSON-массива в ТОМ ЖЕ ФОРМАТЕ, что и на входе.
    -   Ответ должен быть только валидным JSON, без лишних слов.
    -   Язык заголовков: ${currentLanguage}.

    Массив тем для обработки:
    $titlesJsonForPrompt
""".trimIndent()
    }

    private fun createSummaryPrompt(title: String, bannedNews: String, newsText: String, currentLanguage: String): String {
        return """
    Ты — беспристрастный новостной аналитик. Твоя задача — составить структурированное и информативное резюме по заданной новостной теме на основе предоставленных материалов.

    **Тема:** "$title"

    **Инструкции:**
    1.  **Напиши подробное резюме объемом до 300 слов.** Изложи суть события, его причины и возможные последствия. Избегай "воды" и общих фраз, используй только факты из новостей.
    2.  **Структурируй текст.** Если в рамках темы есть несколько ключевых подсобытий, раздели их абзацами (переносом строки `\n`).
    3.  **Отражай разные точки зрения.** Если источники предоставляют противоречивую информацию, упомяни это явно. Например: "По данным одного источника..., однако другой источник сообщает, что...".
    4.  **Недостаток информации:** Если предоставленных новостей недостаточно для полного резюме, напиши краткий обзор на основе имеющихся данных и в конце добавь фразу: "Для полного анализа требуется больше информации."
    5.  **Запрещённые темы:** Не включай в резюме информацию, связанную с темами: "$bannedNews".
    6.  **Контекст:** Текущее время (Unix millis) - ${System.currentTimeMillis()}.

    **Формат ответа:**
    -   Верни СТРОГО один JSON-объект.
    -   Ответ должен начинаться с `{` и заканчиваться `}`. Никаких пояснений или другого текста вне JSON.
    -   Не используй Markdown-форматирование (жирный шрифт, курсив и т.д.).
    -   Язык ответа: ${currentLanguage}.

    **Пример формата ответа:**
    {
      "title": "$title",
      "summary": "Резюме новостного события. Описание ключевых деталей, действующих лиц и последствий. Объем текста — до 300 слов.\nВторое подсобытие в рамках этой же темы, изложенное в новом абзаце."
    }
    
    **Новости для составления резюме:**
    $newsText
""".trimIndent()
    }
}




