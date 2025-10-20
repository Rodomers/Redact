package com.rds.mews

import android.annotation.SuppressLint
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.mutableListOf
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import kotlinx.coroutines.sync.withPermit


private data class SummaryResult(
    val originalTitle: String,
    val summary: String,
    val time: Long,
    val sources: List<String>,
    val links: List<String>
)

// ====== Класс для работы с OpenRouter API ======
// Апи ключ пока захардкожен, потом спокойно можно подвязать другой ключ
// "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"
// "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"
// "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"
// ---------------------------------------------------------------------
// сейчас стоит ключ для cloud ru
// api key d2a02527c52b1e4c1da6b640308b2170
// key ID M2I1MTEyMGQtYmRmYS00MDE4LThhNTItMzhjMTE3ZWVhZmQ4.3979e60d1ecbaa29e535571a7199a4f5
// secret key e586ffd44e5801b67a89adcbf4c1cfd9
// ------------------------------------------------------------------------
@Serializable
class LLMClient(
    // gemini api keys
    // AIzaSyBwT2sBtNulYoVFDpxq4uHPx-S-LCq7aAw
    // AIzaSyCNNpbcjd8lMRMtD6naikNMaRxnG-0HHkk
    val apiKey: String = "AIzaSyCNNpbcjd8lMRMtD6naikNMaRxnG-0HHkk",
    val MODEL: String = "gemini-2.0-flash-lite",
    private val URL: String = "https://generativelanguage.googleapis.com/v1beta/models/${if (MODEL != "") MODEL else "gemini-2.0-flash"}:generateContent"
) {

    // Отправляем запрос к Gemini, получаем текст ответа
    suspend fun sendPrompt(prompt: String): String? {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60000 // 60 секунд
                connectTimeoutMillis = 15000
                socketTimeoutMillis = 60000
            }
        }

        // Создаём тело запроса
        val requestBody = GeminiRequest(
            contents = listOf(
                ContentInput(
                    parts = listOf(PartInput(prompt))
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.5,
                maxOutputTokens = 8192
            )
        )

        // Отправка POST запроса
        val responseString: String = client.post(URL) {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body()

        client.close()

        // Парсим JSON и достаем текст
        val json = Json { ignoreUnknownKeys = true }
        val geminiResponse = json.decodeFromString<GeminiResponse>(responseString)
        geminiResponse.candidates?.let {
            if (!it.isEmpty()) {
                return geminiResponse.candidates
                    .flatMap { it.content.parts }
                    .joinToString("\n") { it.text }
            } else {
                return null
            }
        }
        return null
    }

    // --- Сериализуемые классы для запроса ---
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

    // --- Сериализуемые классы для ответа ---
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

// ====== Логика суммаризации ======
class NewsSummarizer(
    private val db: DbHelper, // Хелпер для БД
    private val llm: LLMClient // клиенты для опенроутера
) {

    /**
     * Этап 1: выделяем темы из новостей и сохраняем в titles
     * Функцию можно впринципе не вызывать, она приватная и нужна только для следующей
     * можно указать сколько тем извлечь
     * можно указать на актуальность новостей в секундах, по стандарту - неделя
     */
    private suspend fun extractTopics(max: Int = 20, messageSeconds: Long = 14515200): Boolean {
        try {
            // Рекомендуется сортировать сообщения по времени, от старых к новым.
            // Это поможет модели уделить больше внимания последним событиям.
            val messages = db.getMessages(messageSeconds).sortedBy { it.time }
            if (messages.isEmpty()) {
                println("Нет новостей для анализа")
                return false
            }
            val combinedNews = messages.joinToString("\n") { "• ${it.mess} (id - ${it.id})" }
            val bannedNews = "Таких тем нет" //db.getBannedTopics() и обработка

            val prompt = """
                Твоя задача — проанализировать список новостей и сгруппировать их по ключевым событиям.

                **Инструкции:**
                1.  **Выдели от 1 до $max главных событий.** Это строгий лимит, который нельзя превышать.
                2.  **Сортируй события по важности:** от самых значимых к менее значимым. Наиболее свежие и резонансные события должны быть выше в списке.
                3.  **Объединяй связанные новости.** Например, сообщения об атаках дронов, закрытии аэропортов и сбитиях БПЛА следует объединять в одну тему: "Атаки БПЛА на российские регионы".
                4.  **Избегай слишком общих тем.** Заголовки вроде "Политика", "Спорт" или "Происшествия" запрещены. Заголовок должен отражать суть события.
                5.  **Не дублируй новости.** Каждая новость (каждый id) может относиться только к ОДНОЙ теме.
                6.  **Игнорируй запрещённые темы:** $bannedNews.
                7.  **Учитывай временной контекст:** Новости, произошедшие позже, как правило, важнее. Текущее время (Unix millis) - ${System.currentTimeMillis()}.

                **Формат ответа:**
                -   Верни ответ СТРОГО в виде JSON-массива.
                -   Никакого текста до или после JSON. Ответ должен начинаться с `[` и заканчиваться `]`.
                -   Все поля в JSON должны быть заполнены.
                -   Язык заголовков: ${MewsRepository.getStringResource(R.string.current_language) ?: "russian"}.

                **Пример формата ответа:**
                [
                  {
                    "title": "Новые санкции против технологического сектора РФ",
                    "id": [101,
                    105,
                    112]
                  },
                  {
                    "title": "Запуск новой линии метро в Москве",
                    "id": [102,
                    108]
                  }
                ]

                **Новости для анализа:**
                $combinedNews
            """.trimIndent()

            val response: String
            try {
                response = withTimeout(60000L) {
                    llm.sendPrompt(prompt) ?: ""
                }
            } catch (e: Exception) {
                println("Превышено время ожидания ответа от нейросети.")
                return false
            }

            val cleanResponse = response.trim().removePrefix("```json").removeSuffix("```").trim()

            val jsonArray = JSONArray(cleanResponse)
            val iterEnd = if (jsonArray.length() <= max) jsonArray.length() else max

            for (i in 0 until iterEnd) {
                val obj: JSONObject = jsonArray.getJSONObject(i)

                val title = obj.getString("title")
                val idsArray = obj.getJSONArray("id")

                val idsLong = mutableListOf<Long>()
                val idsStr = mutableListOf<String>()
                for (j in 0 until idsArray.length()) {
                    // Исправлено: ID сообщений могут быть большими, используем getLong
                    val messageId = idsArray.getLong(j)
                    idsLong.add(messageId)
                    idsStr.add(messageId.toString())
                }

                db.addTitle(
                    titleTime = 0,
                    title = title,
                    text = "<промежуточный текст>",
                    sources = "<промежуточный текст>",
                    links = db.dbPack(*idsStr.toTypedArray())
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false // Добавлено, чтобы функция возвращала false в случае ошибки
        }

        return true
    }

    /**
     * Этап 2: для каждой темы создаем краткое резюме с указанием источников
     * Эту функцию уже вызывать в коде, она загружает всё в бд
     * можно указать максимальное количество тем
     * можно указать время новостей в секундах, по стандарту - неделя
     * можно указать время между запросами, по стандарту - 10 секунд
     */
    private suspend fun filterTopics(maxTopics: Int = 20): Boolean {
        val rawTitles = db.getTitles()
        val titles = mutableListOf<Topics>()
        rawTitles.forEach { title ->
            if (title.text == "<промежуточный текст>" && title.time.toInt() == 0 && title.sources == "<промежуточный текст>") {
                titles.add(Topics(title.title, db.dbUnpack(title.links).map { id -> id.toLong() }))
                db.delTitle(title.id)
            }
        }

        if (titles.isEmpty()) {
            println("Нет тем для фильтрации")
            return true // Не ошибка, просто нет работы
        }

        val bannedNews = "Таких тем нет" //db.getBannedTopics() и обработка
        val titlesJsonForPrompt = JSONArray(
            titles.map { topic ->
                JSONObject().apply {
                    put("title", topic.title)
                    put("ids", JSONArray(topic.ids))
                }
            }
        ).toString()

        val prompt = """
            Твоя задача — отфильтровать и отсортировать новостные темы.

            **Инструкции:**
            1.  **Проанализируй JSON-массив тем.**
            2.  **Удали темы, связанные с запрещёнными категориями:** $bannedNews.
            3.  **Оставь ровно $maxTopics самых важных тем.** Если тем изначально меньше, оставь все.
            4.  **Отсортируй итоговый список по убыванию важности.** Самые свежие и значимые темы должны быть первыми.
            5.  **Объединяй схожие темы, если это возможно**, сохраняя при этом все уникальные ID сообщений.

            **Формат ответа:**
            -   Верни результат в виде JSON-массива в ТОМ ЖЕ ФОРМАТЕ, что и на входе.
            -   Сохраняй оригинальные `ids` для каждой темы.
            -   Ответ должен быть только валидным JSON, без лишних слов. Начинаться с `[` и заканчиваться `]`.
            -   Язык заголовков: ${MewsRepository.getStringResource(R.string.current_language) ?: "russian"}.

            **Массив тем для фильтрации:**
            $titlesJsonForPrompt
        """.trimIndent()
        val response: String
        try {
            response = withTimeout(60000L) {
                llm.sendPrompt(prompt) ?: ""
            }
        } catch (e: Exception) {
            println("Превышено время ожидания ответа от нейросети.")
            return false
        }

        val cleanResponse = response.trim().removePrefix("```json").removeSuffix("```").trim()

        try {
            val jsonArray = JSONArray(cleanResponse)
            val iterEnd = if (jsonArray.length() <= maxTopics) jsonArray.length() else maxTopics

            for (i in 0 until iterEnd) {
                val obj: JSONObject = jsonArray.getJSONObject(i)

                val title = obj.getString("title")
                val idsArray = obj.getJSONArray("ids")

                val idsStr = mutableListOf<String>()
                for (j in 0 until idsArray.length()) {
                    idsStr.add(idsArray.getLong(j).toString())
                }

                db.addTitle(
                    titleTime = 0,
                    title = title,
                    text = "<промежуточный текст>",
                    sources = "<промежуточный текст>",
                    links = db.dbPack(*idsStr.toTypedArray())
                )
            }
        } catch (e: JSONException) {
            println("Ошибка парсинга JSON при фильтрации тем: ${e.message}")
            println("Невалидный ответ от LLM: $cleanResponse")
            return false
        }

        return true
    }


    suspend fun summarizeTopics(
        maxTopics: Int = 20,
        messageSeconds: Long = 14515200,
        readyFunc: () -> Unit,
        filterTopics: Boolean = false
    ): SummarizationResult {
        val repository = MewsRepository

        try {
            val messages = db.getMessages(messageSeconds)
            if (messages.isEmpty()) {
                readyFunc()
                return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE)
            }

            var rawTitles = db.getTitles()
            var titles = mutableListOf<Topics>()
            var flagForUnfinishedTopics = false
            var errFlag: Boolean

            repository.setUpdatingState("extracting_topics")

            // ЭТО ПЛОХО, но я  не знаю как переделать
            rawTitles.forEach { title ->
                if(title.text == "<промежуточный текст>" && title.time.toInt() == 0 && title.sources == "<промежуточный текст>"){
                    titles.add(Topics(title.title,db.dbUnpack(title.links).map { id -> id.toLong() }))
                    // это костыль для восстановления работы
                    // его и такую же строчку ниже не раскомменчивать до глобальной переделки
//                db.delTitle(title.id)
                    flagForUnfinishedTopics = true
                }
            }
            println(titles)

            if (!flagForUnfinishedTopics) {
                errFlag = extractTopics(maxTopics, messageSeconds)
                if (!errFlag) {
                    for (i in 1..2) {
                        errFlag = extractTopics(maxTopics, messageSeconds)
                        if (errFlag) break
                    }

                    if (!errFlag) {
                        readyFunc()
                        return SummarizationResult.Failure(SummarizationErrorType.EXTRACT_TOPICS_FAILED)
                    }
                }

                if (filterTopics) {
                    repository.setUpdatingState("filtering_topics")
                    errFlag = filterTopics(maxTopics)
                    if (!errFlag) {
                        for (i in 1..2) {
                            errFlag = filterTopics(maxTopics)
                            if (errFlag) break
                        }

                        if (!errFlag) {
                            readyFunc()
                            return SummarizationResult.Failure(SummarizationErrorType.FILTER_FAILED)
                        }
                    }
                }
                rawTitles = db.getTitles()
                titles = mutableListOf<Topics>()
                rawTitles.forEach { title ->
                    if(title.text == "<промежуточный текст>" && title.time.toInt() == 0 && title.sources == "<промежуточный текст>"){
                        titles.add(Topics(title.title,db.dbUnpack(title.links).map { id -> id.toLong() }))
//                    db.delTitle(title.id)
                    }
                }
                MewsRepository.setLastTitlesUpdate(System.currentTimeMillis())
            }

            when (titles.size) {
                0 -> {
                    readyFunc()
                    return SummarizationResult.Success
                }
                in maxTopics + 1..Int.MAX_VALUE -> {
                    db.titlesTimeKill(0)
                    readyFunc()
                    return SummarizationResult.Failure(SummarizationErrorType.EXTRACT_TOPICS_FAILED)
                }
                else -> {
                    var counter = 0
                    val titlesCounter = titles.size
                    var emptyAnswer = false

                    val semaphore = Semaphore(2)

                    val summarizedResults = coroutineScope {
                        titles.map { title ->
                            async(Dispatchers.IO) {
                                semaphore.withPermit {
                                    try {
                                        delay(200L)
                                        counter++
                                        repository.setUpdatingState("${counter}/${titlesCounter}")

                                        val suitableMessages: MutableList<Message> = mutableListOf()
                                        title.ids?.forEach { id ->
                                            messages.find { it.id == id }?.let { suitableMessages.add(it) }
                                        }
                                        val newsText = suitableMessages.joinToString("\n") { "— ${it.mess}" }
                                        val bannedNews = "Таких тем нет"

                                        val response = withTimeout(40000L) {
                                            sumTopic(llm, title.title, bannedNews, newsText)
                                        }

                                        if (response.isBlank()) {
                                            println("Пустой ответ от LLM для темы: ${title.title}")
                                            emptyAnswer = true
                                            return@withPermit null
                                        }

                                        val cleanResponse = response.trim().replace("```json", "")
                                            .replace("```", "").replace("\"\"\"", "").trim()

                                        val obj = JSONObject(cleanResponse)
                                        val summary = obj.getString("summary")
                                        val ids = title.ids ?: return@withPermit null

                                        var time = System.currentTimeMillis()
                                        ids.forEach { id -> time = min(db.getMessage(id)?.time ?: Long.MAX_VALUE, time) }

                                        val links = mutableListOf<String>()
                                        val sources = mutableListOf<String>()
                                        ids.forEach { id ->
                                            db.getMessage(id)?.let { mess ->
                                                links.add(mess.link)
                                                sources.add(mess.source)
                                            }
                                        }

                                        SummaryResult(title.title, summary, time, sources, links)

                                    } catch (e: Exception) {
                                        println("Ошибка при обработке темы '${title.title}': ${e.message}")
                                        return@withPermit null
                                    }
                                }
                            }
                        }.mapNotNull { it.await() }
                    }

                    if (summarizedResults.isNotEmpty()) {
                        summarizedResults.forEach { result ->
                            println("Обновление в БД: ${result.originalTitle}")
                            db.updateTitle(
                                name = result.originalTitle,
                                newTime = result.time,
                                newText = result.summary,
                                newSources = db.dbPack(*result.sources.toTypedArray()),
                                newLinks = db.dbPack(*result.links.toTypedArray())
                            )
                        }
                    }

                    if (summarizedResults.size != titles.size) {
                        return when (emptyAnswer) {
                            true -> SummarizationResult.Failure(SummarizationErrorType.EMPTY_ANSWER)
                            else -> SummarizationResult.Failure(SummarizationErrorType.SUMMARIZE_TOPICS_FAILED)
                        }
                    }
                }
            }

            readyFunc()
            return SummarizationResult.Success
        } catch(e: Exception) {
            e.printStackTrace()
            readyFunc()
            return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private suspend fun sumTopic(llm: LLMClient, title: String, bannedNews: String, newsText: String): String {
        try{
            val prompt = """
                Твоя задача — составить краткое, но информативное резюме по заданной новостной теме.

                **Тема:** "$title"

                **Инструкции:**
                1.  **Напиши подробное резюме.** Изложи суть события, избегая "воды" и общих фраз.
                2.  **Структурируй текст.** Если в рамках одной темы есть несколько подсобытий, раздели их переносом строки (`\n`).
                3.  **Отражай разные точки зрения.** Если источники предоставляют противоречивую информацию, упомяни обе позиции.
                4.  **Игнорируй запрещённые темы:** $bannedNews.
                5.  **Учитывай временной контекст:** Текущее время (Unix millis) - ${System.currentTimeMillis()}.

                **Формат ответа:**
                -   Верни СТРОГО один JSON-объект.
                -   Ответ должен начинаться с `{` и заканчиваться `}`. Никаких пояснений или другого текста вне JSON.
                -   Все поля должны быть заполнены.
                -   Не используй Markdown-форматирование (жирный шрифт, курсив и т.д.).
                -   Язык ответа: ${MewsRepository.getStringResource(R.string.current_language) ?: "russian"}.

                **Пример формата ответа:**
                {
                  "title": "$title",
                  "summary": "Резюме новостного события. Описание ключевых деталей, действующих лиц и последствий.\nВторое подсобытие в рамках этой же темы."
                }
                
                **Новости для составления резюме:**
                $newsText
            """.trimIndent()

        val response = llm.sendPrompt(prompt) ?: ""
            println(response)
        return response
        } catch (e: Exception){
            return ""
        }
    }

    data class Topics (
        val title: String,
        val ids: List<Long>?
    )
}



