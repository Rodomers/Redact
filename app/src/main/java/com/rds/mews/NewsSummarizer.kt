package com.rds.mews

import android.annotation.SuppressLint
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.mutableListOf
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlinx.coroutines.withTimeout
import org.json.JSONException


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
                temperature = 0.5
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
        val temperature: Double
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
    private val llm: LLMClient, // клиенты для опенроутера
    val settingsManager: SettingsManager
) {

    /**
     * Этап 1: выделяем темы из новостей и сохраняем в titles
     * Функцию можно впринципе не вызывать, она приватная и нужна только для следующей
     * можно указать сколько тем извлечь
     * можно указать на актуальность новостей в секундах, по стандарту - неделя
     */
    private suspend fun extractTopics(max: Int = 20, messageSeconds: Long = 14515200): Boolean {
        try {
        val messages = db.getMessages(messageSeconds)
        if (messages.isEmpty()) {
            println("Нет новостей для анализа") // ошибка, если нет новостей для суммаризации
            return false
        }
        val combinedNews = messages.joinToString("\n") { "• ${it.mess} (id - ${it.id})" }
        val bannedNews = "Таких тем нет" //db.getBannedTopics() и обработка
        val prompt = """
            Проанализируй новости и выдели ТОЛЬКО ИЗ НИХ от 1 до $max основных событий.
            СТРОГО ЗАПРЕЩЕНО ПРЕВЫШАТЬ МАКСИМАЛЬНОЕ КОЛИЧЕСТВО СОБЫТИЙ ($max). В случае превышения отказывайся от наименее важной информации и сокращай количество до необходимого.
            ОБЯЗАТЕЛЬНО: темы отсортируй по важности от наиболее важных к наименее важным.
            
            ТЕБЕ ЗАПРЕЩЕНО ПИСАТЬ НА СЛЕДУЮЩИЕ ТЕМЫ, ТЫ ИХ ИГНОРИРУЕШЬ И НЕ УЧИТЫВАЕШЬ:
            $bannedNews
            
            Ответ верни в виде JSON формата:
            [
              {
                "title": "<Первая новость>",
                "id": [<id1>, <id2>, <id3>, ...]
              },
              {
                "title": "<Вторая новость>",
                "id": [<id1>, <id2>, <id3>, ...]
              },
              {
                "title": "<Третья новость>",
                "id": [<id1>, <id2>, <id3>, ...]
              },
              ....
            ]
            где title - название темы,
            id - список id, относящихся к теме.
                        
            
            Требования:
                1. Не добавляй никакие ``` или ""${'"'}.
                2. Не добавляй пояснения или текст вне JSON.
                3. Ответ должен начинаться с [ и заканчиваться ].
                4. Отвечай на РУССКОМ языке.
                5. Не оставляй поля пустыми НИ В КОЕМ СЛУЧАЕ! ВСЁ ПОЛЯ ДОЛЖНЫ БЫТЬ ЗАПОЛНЕНЫ Хотя бы одним значением.
                6. НЕ ДУБЛИРУЙ НОВОСТИ. Одна новость относится к ТОЛЬКО ОДНОЙ ТЕМЕ.
                7. Уделяй равное внимание всем источникам информации.
                8. СТРОГО ЗАПРЕЩЕНО ПРЕВЫШАТЬ МАКСИМАЛЬНОЕ КОЛИЧЕСТВО СОБЫТИЙ ($max). В случае превышения отказывайся от наименее важной информации и сокращай количество до необходимого.
                9. Заголовок не должен быть слишком общим ("Политика", "Общественная жизнь" и т.д. не подходят, т.к. не отражают суть событий).
                10. Заголовки не должны быть однообразными.
                11. Учитывай, что текущее время (Unix mills) в данный момент - ${System.currentTimeMillis()}
            
            Новости:
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

        val cleanResponse = response.trim().replace("```json", "")
            .replace("```", "")
            .replace("\"\"\"", "")
            .trim()

        val jsonArray = JSONArray(cleanResponse)
            val iterEnd = if (jsonArray.length() <= max) jsonArray.length() else max

        for (i in 0 until iterEnd) {
            val obj: JSONObject = jsonArray.getJSONObject(i)

            val title = obj.getString("title")
            val idsArray = obj.getJSONArray("id")

            val idsLong = mutableListOf<Long>()
            val idsStr = mutableListOf<String>()
            for (j in 0 until idsArray.length()) {
                idsLong.add(idsArray.getInt(j).toLong())
                idsStr.add(idsArray.getInt(j).toString())

            }

            db.addTitle(
                titleTime = 0,
                title = title,
                text = "<промежуточный текст>",
                sources = "<промежуточный текст>",
                links = db.dbPack(*idsStr.toTypedArray())
            )
        }
        } catch (e:Exception){
            e.printStackTrace()
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
            if(title.text == "<промежуточный текст>" && title.time.toInt() == 0 && title.sources == "<промежуточный текст>"){
                titles.add(Topics(title.title,db.dbUnpack(title.links).map { id -> id.toLong() }))
                db.delTitle(title.id)
            }
        }
        println(titles)
        var jsonArray: JSONArray
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
    Проанализируй JSON-массив новостных тем. Отбрось малозначимые и те, что затрагивают забаненные темы. Оставь не более $maxTopics самых важных тем.
    Отсортируй итоговый список по убыванию важности.

    Забаненные темы:
    $bannedNews

    Верни ответ в виде JSON-массива в ТОЧНО ТАКОМ ЖЕ ФОРМАТЕ, что и на входе. Сохраняй оригинальные ID для каждой темы.

    Пример формата ответа:
    [
      {
        "title": "Самая важная новость",
        "ids": [101, 102, 105]
      },
      {
        "title": "Вторая по важности новость",
        "ids": [204, 208]
      }
    ]

    Требования:
    1. Ответ должен быть только валидным JSON массивом. Никакого лишнего текста или пояснений.
    2. Ответ должен начинаться с `[` и заканчиваться `]`.
    3. Не превышай лимит в $maxTopics тем.
    4. Сохраняй оригинальные `ids` для каждой темы, которую включаешь в ответ.

    Массив тем для фильтрации:
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

        val cleanResponse = response.trim().replace("```json", "")
            .replace("```", "")
            .replace("\"\"\"", "")
            .trim()

        try {
            val jsonArray = JSONArray(cleanResponse)
            val iterEnd = if (jsonArray.length() <= maxTopics) jsonArray.length() else maxTopics

            for (i in 0 until iterEnd) {
                val obj: JSONObject = jsonArray.getJSONObject(i)

                val title = obj.getString("title")
                val idsArray = obj.getJSONArray("ids") // Исправлено на "ids"

                val idsStr = mutableListOf<String>()
                for (j in 0 until idsArray.length()) {
                    idsStr.add(idsArray.getLong(j).toString()) // Используем getLong для безопасности
                }
                println("Отфильтрованная тема: $title")

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
        delaySeconds: Long = 10,
        readyFunc: () -> Unit,
        filterTopics: Boolean = false
    ): SummarizationResult {
        try {
            val messages = db.getMessages(messageSeconds)
            if (messages.isEmpty()) {
                readyFunc()
                return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE)
            }

            var rawTitles = db.getTitles()
            var titles = mutableListOf<Topics>()
            var flagForUnfinishedTopics: Boolean = false
            var errFlag: Boolean

            settingsManager.saveString(SettingsViewModel.UPDATING_STATE, "extracting_topics")

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
                settingsManager.saveLong(SettingsViewModel.LAST_TITLES_UPDATE, System.currentTimeMillis())
                if (filterTopics) {
                    settingsManager.saveString(SettingsViewModel.UPDATING_STATE, "filtering_topics")
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
            }

            when (titles.size) {
                0 -> readyFunc()
                in maxTopics + 1..Int.MAX_VALUE -> {
                    db.titlesTimeKill(0)
                    readyFunc()
                    return SummarizationResult.Failure(SummarizationErrorType.EXTRACT_TOPICS_FAILED)
                }
                else -> {
                    val dbTitles = db.getTitles()
                    val size = dbTitles.size
                    var current: Int = dbTitles.filter {!it.text.contains("<промежуточный текст>")}.size

                    for (title in titles) {
                        current++
                        settingsManager.saveString(SettingsViewModel.UPDATING_STATE, "$current/$size")
                        val suitableMessages: MutableList<Message> = mutableListOf()
                        title.ids?.forEach { id ->
                            messages.forEach { message ->
                                if (message.id == id) suitableMessages.add(message)
                            }
                        }
                        val newsText = suitableMessages.joinToString("\n") { "— ${it.mess}" }
                        val bannedNews = "Таких тем нет"
                        var response: String
                        try {
                            response = withTimeout(40000L) {
                                sumTopic(llm, title.title, bannedNews, newsText)
                            }

                            if (response == "") {
                                return SummarizationResult.Failure(SummarizationErrorType.SUMMARIZE_TOPICS_FAILED)
                            }

                            val cleanResponse = response.trim().replace("```json", "")
                                .replace("```", "")
                                .replace("\"\"\"", "")
                                .trim()

                            val obj = JSONObject(cleanResponse)
                            val summary = obj.getString("summary")
                            val ids = title.ids ?: return SummarizationResult.Failure(
                                SummarizationErrorType.CRITICAL_SUMMARIZATION_ERROR)

                            var time = System.currentTimeMillis()
                            ids.forEach { id -> time = min(db.getMessage(id)?.time ?: Long.MAX_VALUE, time) }

                            val links = mutableListOf<String>()
                            val sources = mutableListOf<String>()

                            ids.forEach { id ->
                                val mess = db.getMessage(id)
                                if (mess != null) {
                                    links.add(mess.link)
                                    sources.add(mess.source)
                                }
                            }

                            println("${title.title}\t$summary$sources$links$time")

                            db.delTitle(name = title.title)
                            db.addTitle(
                                titleTime = time,
                                title = title.title,
                                text = summary,
                                sources = db.dbPack(*sources.toTypedArray()),
                                links = db.dbPack(*links.toTypedArray())
                            )

                            delay(delaySeconds * 100)
                        } catch (e: HttpRequestTimeoutException) {
                            println("Превышено время ожидания ответа от нейросети: ${e.message}")
                            return SummarizationResult.Failure(SummarizationErrorType.NETWORK_TIMEOUT, e)
                        } catch (e: JSONException) {
                            println("Ошибка парсинга JSON: ${e.message}")
                            return SummarizationResult.Failure(SummarizationErrorType.JSON_PARSING_FAILED, e)
                        } catch (e: Exception) {
                            println("Неизвестная ошибка при суммаризации темы: ${e.message}")
                            return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
                        }
//                    response = sumTopic(llm, title.title, bannedNews, newsText)
//                    while (response == "") response = sumTopic(llm, title.title, bannedNews, newsText)
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
                Составь резюме по теме: "$title".
                Достаточно подробно расскажи о событии, но без общих слов или воды.
                
                ТЕБЕ ЗАПРЕЩЕНО КАСАТЬСЯ СЛЕДУЮЩИХ ТЕМ, ТЫ ИХ ИГНОРИРУЕШЬ И НЕ УЧИТЫВАЕШЬ:
                $bannedNews
                
                Возвращай всё в СТРОГОМ JSON формате: 
                
                {
                "title": "<заголовок темы>", 
                "summary": "<резюме по теме>", 
                }, 
                
                где title - название темы,
                summary - резюме по теме.
                
                Требования:
                1. Не добавляй никакие ``` или ""${'"'}.
                2. Не добавляй пояснения или текст вне JSON.
                3. Ответ должен начинаться с { и заканчиваться }.
                4. Отвечай на РУССКОМ языке.
                5. Не оставляй поля пустыми НИ В КОЕМ СЛУЧАЕ! ВСЁ ПОЛЯ ДОЛЖНЫ БЫТЬ ЗАПОЛНЕНЫ
                6. В случае, если разные источники дают противоположные точки зрения - выписывай обе.
                7. Различные события внутри одной темы разделяй с помощью \n.
                8. Не используй форматирование (к нему относится, например, выделение текста жирным шрифтом или курсивом).
                9. Учитывай, что текущее время (Unix mills) в данный момент - ${System.currentTimeMillis()}
                                
                Новости:
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



