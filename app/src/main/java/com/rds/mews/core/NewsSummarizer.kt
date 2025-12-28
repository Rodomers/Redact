package com.rds.mews.core

import android.annotation.SuppressLint
import com.rds.mews.localcore.Message
import com.rds.mews.localcore.SettingsManager
import com.rds.mews.localcore.SummarizationErrorType
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.repositories.MewsRepository
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlin.math.min
import java.io.Closeable
import kotlin.math.pow

class LLMClient(
    val apiKey: String = "",
    val MODEL: String = MewsRepository.defaultModel.key,
    private val URL_TEMPLATE: String = "https://generativelanguage.googleapis.com/v1beta/models/%MODEL%:generateContent",
    enableProxy: Boolean = false
) : Closeable {

    private val finalUrl = URL_TEMPLATE.replace(
        "%MODEL%",
        MODEL.ifBlank { MewsRepository.defaultModel.key }
    )

    private val httpClient = SharedHttpClient.createInstance(
        MewsRepository.PROXY_ADDRESS,
        MewsRepository.SERVER_KEY,
        enableProxy = enableProxy
    )
    private val jsonParser = SharedHttpClient.jsonParser

    private val MAX_RETRIES = 3

    suspend fun sendPrompt(prompt: String): String? {
        val requestBodyObj = GeminiRequest(
            contents = listOf(ContentInput(parts = listOf(PartInput(prompt)))),
            generationConfig = GenerationConfig(temperature = 0.5, maxOutputTokens = 8192)
        )

        val requestBodyString = jsonParser.encodeToString(requestBodyObj)

        var attempt = 0
        while (attempt <= MAX_RETRIES) {
            try {
                val response = httpClient.post(
                    url = finalUrl,
                    body = requestBodyString,
                    headers = mapOf(
                        "x-goog-api-key" to apiKey,
                        "Content-Type" to "application/json"
                    )
                )

                if (response.status == 429) {
                    attempt++
                    if (attempt > MAX_RETRIES) {
                        println("GEMINI QUOTA EXCEEDED: Gave up after $MAX_RETRIES retries.")
                        return null
                    }
                    val waitTime = 2000L * (2.0.pow(attempt - 1).toLong())
                    println("GEMINI QUOTA EXCEEDED (429). Waiting ${waitTime}ms before retry #$attempt...")
                    delay(waitTime)
                    continue
                }

                val responseString = response.body

                println("--- RAW GEMINI RESPONSE ---")
                println("Status Code: ${response.status}")
                if (response.status != 200) {
                    println("GEMINI API ERROR: HTTP ${response.status}")
                    return null
                }

                println("Body received (len: ${responseString.length})")
                println("---------------------------")

                val geminiResponse = jsonParser.decodeFromString<GeminiResponse>(responseString)

                if (geminiResponse.error != null) {
                    println("API ERROR: ${geminiResponse.error.message}")
                    if (geminiResponse.error.message?.contains("quota", true) == true) {
                        attempt++
                        if (attempt > MAX_RETRIES) return null
                        delay(5000L)
                        continue
                    }
                    return null
                }

                if (geminiResponse.promptFeedback?.blockReason != null) {
                    println("PROMPT BLOCKED: ${geminiResponse.promptFeedback.blockReason}")
                    return null
                }

                return geminiResponse.candidates
                    ?.takeIf { it.isNotEmpty() }
                    ?.flatMap { candidate -> candidate.content?.parts ?: emptyList() }
                    ?.joinToString("\n") { part -> part.text }

            } catch (e: Exception) {
                println("Error inside sendPrompt logic: ${e.message}")
                e.printStackTrace()
                return null
            }
        }
        return null
    }

    override fun close() {
        httpClient.close()
    }

    @Serializable data class GeminiRequest(val contents: List<ContentInput>, val generationConfig: GenerationConfig? = null)
    @Serializable data class GenerationConfig(val temperature: Double, val maxOutputTokens: Int? = null)
    @Serializable data class ContentInput(val parts: List<PartInput>)
    @Serializable data class PartInput(val text: String)
    @Serializable data class GeminiResponse(val candidates: List<Candidate>? = null, val error: GeminiError? = null, val promptFeedback: PromptFeedback? = null)
    @Serializable data class GeminiError(val code: Int? = null, val message: String? = null, val status: String? = null)
    @Serializable data class PromptFeedback(val blockReason: String? = null)
    @Serializable data class Candidate(val content: Content?, val finishReason: String? = null)
    @Serializable data class Content(val parts: List<Part>)
    @Serializable data class Part(val text: String)
}

suspend fun validateGeminiKey(
    apiKey: String,
    proxyIp: String,
    proxyKey: String,
    enableProxy: Boolean
): Boolean {
    val client = SharedHttpClient.createInstance(
        serverIp = proxyIp,
        rssHubKey = proxyKey,
        enableProxy = enableProxy
    )

    return try {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val response = client.get(url)
        response.status == 200
    } catch (e: Exception) {
        e.printStackTrace()
        false
    } finally {
        client.close()
    }
}

class NewsSummarizer(
    private val db: DbHelper,
    private val llm: LLMClient,
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
        val msgIds: List<Long>
    )

    private val SUMMARY_BATCH_SIZE = 7
    private val NEWS_BATCH_SIZE = 40

    suspend fun summarizeTopics(
        maxTopics: Int = 20,
        messageSeconds: Long = 14515200,
        readyFunc: () -> Unit,
        settingsManager: SettingsManager,
        filterTopics: Boolean = false
    ): SummarizationResult {
        try {
            val messages = db.getMessages(messageSeconds).sortedBy { it.time }
            if (messages.isEmpty()) {
                readyFunc()
                return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE)
            }

            val unfinishedTitles = db.getTitles().filter {
                it.text == "<промежуточный текст>" && it.time.toInt() == 0
            }

            if (unfinishedTitles.isEmpty()) {
                val currentLanguage = settingsManager.getString(MewsRepository.CURRENT_LANGUAGE, "russian")
                val success = processNewsInBatches(maxTopics, messages, currentLanguage, filterTopics, settingsManager)

                if (!success) {
                    readyFunc()
                    return SummarizationResult.Failure(SummarizationErrorType.EXTRACT_TOPICS_FAILED)
                }
                settingsManager.saveLong(MewsRepository.LAST_TITLES_UPDATE, System.currentTimeMillis())
            }

            val titlesToSummarize = db.getTitles().filter {
                it.text == "<промежуточный текст>"
            }.map {
                Topics(it.title, db.dbUnpack(it.ids).mapNotNull { id -> id.toLongOrNull() })
            }

            if (titlesToSummarize.isEmpty()) {
                readyFunc()
                return SummarizationResult.Success
            }

            val titleBatches = titlesToSummarize.chunked(SUMMARY_BATCH_SIZE)

            var processedBatches = 0
            val totalBatches = titleBatches.size
            var emptyAnswer = false
            val semaphore = Semaphore(1)

            val currentProgress = settingsManager.getFloat(MewsRepository.UPDATING_PROGRESS, 0f)
            val remainingProgress = 0.95f - currentProgress

            val summarizedResults = coroutineScope {
                titleBatches.map { batch ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                processedBatches++
                                if (processedBatches > 1) delay(4000L)

                                settingsManager.saveString(MewsRepository.UPDATING_STATE, "summarizing_topics")
                                settingsManager.saveFloat(
                                    MewsRepository.UPDATING_PROGRESS,
                                    (currentProgress + remainingProgress * processedBatches / totalBatches).coerceIn(0f, 0.95f)
                                )
                                val currentLanguage = settingsManager.getString(MewsRepository.CURRENT_LANGUAGE, "russian")

                                val topicsData = batch.mapNotNull { topic ->
                                    val suitableMessages = topic.ids?.mapNotNull { id ->
                                        var msg = messages.find { it.id == id }
                                        if (msg == null) msg = db.getMessage(id)
                                        msg
                                    } ?: emptyList()
                                    val newsText = suitableMessages.joinToString("\n") { "— ${it.mess}" }

                                    if (newsText.isBlank()) null
                                    else Triple(topic, suitableMessages, newsText)
                                }

                                if (topicsData.isEmpty()) return@withPermit emptyList<SummaryResult>()

                                val bannedNews = MewsRepository.bannedNewsFlow.value.joinToString("'; '")

                                val response = withTimeout(150000L) {
                                    sumTopicsBatch(llm, topicsData, bannedNews, currentLanguage)
                                }

                                if (response.isBlank()) {
                                    emptyAnswer = true
                                    return@withPermit emptyList<SummaryResult>()
                                }

                                val jsonArray = safeParseJsonArray(response)
                                val results = mutableListOf<SummaryResult>()

                                for (i in 0 until jsonArray.length()) {
                                    try {
                                        val obj = jsonArray.getJSONObject(i)
                                        val topicTitle = obj.getString("title")
                                        val summary = obj.getString("summary")

                                        val originalData = topicsData.find { it.first.title == topicTitle }

                                        if (originalData != null) {
                                            val (topic, suitableMessages, _) = originalData
                                            val time = suitableMessages.minOfOrNull { it.time } ?: System.currentTimeMillis()

                                            val msgIds = suitableMessages.map { it.id }
                                            val sources = suitableMessages.map { it.source }

                                            results.add(SummaryResult(
                                                originalTitle = topic.title,
                                                summary = summary,
                                                time = time,
                                                sources = sources.distinct(),
                                                msgIds = msgIds.distinct()
                                            ))
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                return@withPermit results

                            } catch (e: Exception) {
                                e.printStackTrace()
                                return@withPermit emptyList<SummaryResult>()
                            }
                        }
                    }
                }.awaitAll().flatten()
            }

            if (summarizedResults.isNotEmpty()) {
                summarizedResults.forEach { result ->
                    db.updateTitle(
                        name = result.originalTitle,
                        newTime = result.time,
                        newText = result.summary,
                        newSources = db.dbPack(*result.sources.toTypedArray()),
                        newLinks = db.dbPack(*result.msgIds.map { it.toString() }.toTypedArray())
                    )
                }
            }

            if (summarizedResults.isEmpty() && titlesToSummarize.isNotEmpty()) {
                return if (emptyAnswer) {
                    SummarizationResult.Failure(SummarizationErrorType.EMPTY_ANSWER)
                } else {
                    SummarizationResult.Failure(SummarizationErrorType.SUMMARIZE_TOPICS_FAILED)
                }
            }

            readyFunc()
            return SummarizationResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            readyFunc()
            return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
        }
    }

    private suspend fun processNewsInBatches(
        maxTopics: Int,
        messages: List<Message>,
        currentLanguage: String,
        filterTopics: Boolean = false,
        settingsManager: SettingsManager
    ): Boolean {
        val uniqueMessages = messages.distinctBy { it.id }
        val messageBatches = uniqueMessages.chunked(NEWS_BATCH_SIZE)
        val allExtractedTopics = mutableListOf<Topics>()

        val extractSemaphore = Semaphore(2)

        try {
            settingsManager.saveString(MewsRepository.UPDATING_STATE, "extracting_topics")
            settingsManager.saveFloat(
                MewsRepository.UPDATING_PROGRESS, (settingsManager.getFloat(
                    MewsRepository.UPDATING_PROGRESS, 0.1f
                ) + 0.1f).coerceIn(0f, 0.2f)
            )

            coroutineScope {
                messageBatches.map { batch ->
                    async(Dispatchers.IO) {
                        extractSemaphore.withPermit {
                            extractTopicsFromBatch(batch, maxTopics, currentLanguage)
                        }
                    }
                }.forEach { deferred ->
                    deferred.await()?.let { topicsFromBatch ->
                        allExtractedTopics.addAll(topicsFromBatch)
                    }
                }
            }

            if (allExtractedTopics.isEmpty()) {
                return false
            }

            return if (filterTopics) {
                settingsManager.saveString(MewsRepository.UPDATING_STATE, "filtering_topics")
                settingsManager.saveFloat(
                    MewsRepository.UPDATING_PROGRESS, (settingsManager.getFloat(
                        MewsRepository.UPDATING_PROGRESS, 0.1f
                    ) + 0.1f).coerceIn(0f, 0.3f)
                )
                mergeAndFilterTopics(allExtractedTopics, maxTopics, currentLanguage)
            } else {
                allExtractedTopics.forEachIndexed { index, batch ->
                    if (index < maxTopics) {
                        val idsStr = batch.ids?.map { it.toString() } ?: listOf("")

                        db.addTitle(
                            title = batch.title,
                            titleTime = 0,
                            text = "<промежуточный текст>",
                            sources = "<промежуточный текст>",
                            links = db.dbPack(*idsStr.toTypedArray())
                        )
                    }
                }
                true
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun extractTopicsFromBatch(messagesBatch: List<Message>, max: Int, currentLanguage: String): List<Topics>? {
        val combinedNews = messagesBatch.joinToString("\n") { "• ${it.mess} (id - ${it.id})" }
        val bannedNews = MewsRepository.bannedNewsFlow.value.joinToString("'; '")

        val prompt = """
    Ты — профессиональный новостной редактор. Твоя задача — сгруппировать новости по событиям.

    Инструкции:
    1.  **Группировка:** Выдели от 1 до $max главных событий.
    2.  **Заголовки:** Придумай яркие, живые заголовки (5-7 слов), отражающие суть.
    3.  **Мелкие новости:** Если новость не тянет на отдельную тему, добавь её в группу "Другие события".
    4.  **УНИКАЛЬНОСТЬ (ВАЖНО):** Один ID новости может принадлежать ТОЛЬКО ОДНОЙ теме. Не дублируй новости.
    5.  **Фильтр:** Исключи темы, похожие на: '$bannedNews'.
    6.  **Контекст:** Время - ${System.currentTimeMillis()}.

    Верни ТОЛЬКО JSON-массив:
    [{"title": "Заголовок темы", "id": [101, 105]}]
    Язык заголовков: $currentLanguage.

    Новости:
    $combinedNews
""".trimIndent()

        try {
            val response = withTimeout(60000L) { llm.sendPrompt(prompt) ?: "" }
            if (response.isBlank()) return null

            val jsonArray = safeParseJsonArray(response)
            val topics = mutableListOf<Topics>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val title = obj.getString("title")
                    val idsArray = obj.getJSONArray("id")
                    val ids = (0 until idsArray.length()).map { idsArray.getLong(it) }
                    topics.add(Topics(title, ids))
                } catch (e: Exception) {
                    continue
                }
            }

            return topics
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun mergeAndFilterTopics(topics: List<Topics>, maxTopics: Int, currentLanguage: String): Boolean {
        val titlesJsonForPrompt = JSONArray(
            topics.map { topic ->
                JSONObject().apply {
                    put("title", topic.title)
                    put("ids", JSONArray(topic.ids))
                }
            }
        ).toString()

        val bannedNews = MewsRepository.bannedNewsFlow.value.joinToString("'; '")

        val prompt = """
    Ты — главный редактор. Твоя задача — объединить дублирующиеся темы и отфильтровать лишнее.

    Входящие данные: массив объектов {"t": "Заголовок", "i": [id новостей]}.

    Инструкции:
    1.  **Слияние:** Если темы "Атака дронов" и "БПЛА ударили по заводу" об одном и том же — объедини их в одну. При объединении ОБЯЗАТЕЛЬНО сложи их списки ID ("i").
    2.  **Топ тем:** Оставь ровно $maxTopics самых важных тем.
    3.  **Заголовки:** Сделай их ёмкими и информативными (news style).
    4.  **Запрет:** Удали темы из списка: '$bannedNews'.
    5.  **Важно:** Не теряй ID новостей при слиянии.

    Верни JSON-массив в формате: [{"title": "Новый заголовок", "ids": [1, 2, 3]}]
    Язык: $currentLanguage.

    Темы:
    $titlesJsonForPrompt
""".trimIndent()

        try {
            val response = withTimeout(60000L) { llm.sendPrompt(prompt) ?: "" }
            if (response.isBlank()) return false

            val jsonArray = safeParseJsonArray(response)
            val iterEnd = min(jsonArray.length(), maxTopics)

            db.titlesTimeKill(0)

            for (i in 0 until iterEnd) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val title = obj.getString("title")
                    val idsArray = obj.getJSONArray("ids")
                    val idsStr = (0 until idsArray.length()).map { idsArray.getLong(it).toString() }

                    db.addTitle(
                        titleTime = 0,
                        title = title,
                        text = "<промежуточный текст>",
                        sources = "<промежуточный текст>",
                        links = db.dbPack(*idsStr.toTypedArray())
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private suspend fun sumTopicsBatch(
        llm: LLMClient,
        batchData: List<Triple<Topics, List<Message>, String>>,
        bannedNews: String,
        currentLanguage: String
    ): String {

        val jsonInput = JSONArray()
        batchData.forEach { (topic, _, text) ->
            val item = JSONObject()
            item.put("title", topic.title)
            item.put("news_content", text)
            jsonInput.put(item)
        }

        val prompt = """
    Ты — редактор интеллектуального новостного дайджеста. Твой стиль — "Smart Casual": ты пишешь увлекательно и живо, но уважаешь интеллект читателя.
    
    Вот JSON с темами и данными ("content"). Для КАЖДОЙ темы создай объект ответа.

    **Правила написания саммари (summary):**
    1.  **Тон и Стиль:**
        -   Пиши так, будто рассказываешь интересную историю умному другу. Используй нормальный человеческий язык, избегай канцеляризмов ("осуществлена деятельность") и штампов.
        -   **Анти-кликбейт:** Избегай истеричных интонаций, лишних восклицательных знаков и фраз вроде "Шок!", "Срочно!", "Вы не поверите".
        -   Сохраняй объективность, но подавай материал динамично.

    2.  **Структура:**
        -   Сразу переходи к сути: что случилось и почему это важно.
        -   Вместо кричащего "хука" используй интересный факт или контекст, который сразу погружает в тему.
        -   Кратко опиши последствия или значение события.
        -   Объем: до 300 слов.

    3.  **Технические требования:**
        -   Используй символы новой строки (`\n`) для разделения абзацев.
        -   Никакого Markdown внутри JSON-значений.
        -   Если тема касается '$bannedNews', верни пустую строку в summary.

    **Формат ответа (строго JSON массив):**
    [
      {"title": "Исходный заголовок (копия)", "summary": "Текст твоего обзора..."}
    ]
    
    Язык вывода: $currentLanguage.

    Входные данные:
    $jsonInput
""".trimIndent()

        try {
            return llm.sendPrompt(prompt) ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    private fun safeParseJsonArray(jsonString: String): JSONArray {
        val cleanJson = jsonString.trim().removePrefix("```json").removeSuffix("```").trim()
        try {
            return JSONArray(cleanJson)
        } catch (e: JSONException) {
            val lastObjectEnd = cleanJson.lastIndexOf('}')

            if (lastObjectEnd != -1) {
                val fixedJson = cleanJson.take(lastObjectEnd + 1) + "]"
                try {
                    return JSONArray(fixedJson)
                } catch (e2: Exception) {
                    return JSONArray()
                }
            }
            return JSONArray()
        }
    }
}