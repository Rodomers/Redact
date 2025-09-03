package com.rds.mews

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.collections.mutableListOf
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray


// ====== Класс для работы с OpenRouter API ======
@Serializable
class LLMClient(
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
    // gemini api keys
    // AIzaSyBwT2sBtNulYoVFDpxq4uHPx-S-LCq7aAw
    // AIzaSyCNNpbcjd8lMRMtD6naikNMaRxnG-0HHkk
    val apiKey: String = "AIzaSyCNNpbcjd8lMRMtD6naikNMaRxnG-0HHkk",
    val MODEL: String = "gemini-2.5-flash-lite",
    private val URL: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent"
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
                requestTimeoutMillis = 180000 // 60 секунд
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
) {

    /**
     * Этап 1: выделяем темы из новостей и сохраняем в titles
     * Функцию можно впринципе не вызывать, она приватная и нужна только для следующей
     * можно указать сколько тем извлечь
     * можно указать на актуальность новостей в секундах, по стандарту - неделя
     */
    private suspend fun extractTopics(max: Int = 20, messageSeconds: Long = 14515200){
        try {
        val messages = db.getMessages(messageSeconds)
        if (messages.isEmpty()) {
            println("Нет новостей для анализа") // ошибка, если нет новостей для суммаризации
        }
        val combinedNews = messages.joinToString("\n") { "• ${it.mess} (id - ${it.id})" }
        val bannedNews = "Таких тем нет" //db.getBannedTopics() и обработка
            var jsonArray: JSONArray
            var iters = 0
            do {
                val prompt = """
            Проанализируй новости и выдели ТОЛЬКО ИЗ НИХ РОВНО $max основных событий.
            СТРОГО ЗАПРЕЩЕНО ПРЕВЫШАТЬ МАКСИМАЛЬНОЕ КОЛИЧЕСТВО СОБЫТИЙ ($max). В случае превышения отказывайся от наименее важной информации и сокращай количество до необходимого.
            
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
            
            Новости:
            $combinedNews
        """.trimIndent()

                val response = llm.sendPrompt(prompt) ?: ""

                val cleanResponse = response.trim().replace("```json", "")
                    .replace("```", "")
                    .replace("\"\"\"", "")
                    .trim()

                jsonArray = JSONArray(cleanResponse)
                iters++
                println("iter: $iters")
            } while (jsonArray.length() > max * 2 && iters <= 3)

        for (i in 0 until jsonArray.length()) {
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
    }

    /**
     * Этап 2: для каждой темы создаем краткое резюме с указанием источников
     * Эту функцию уже вызывать в коде, она загружает всё в бд
     * можно указать максимальное количество тем
     * можно указать время новостей в секундах, по стандарту - неделя
     * можно указать время между запросами, по стандарту - 10 секунд
     */
    suspend fun summarizeTopics(maxTopics: Int = 20, messageSeconds: Long = 14515200, delaySeconds: Long = 10, readyFunc: () -> Unit) {


        val messages = db.getMessages(messageSeconds)
        var rawTitles = db.getTitles()
        var titles = mutableListOf<Topics>()
        var flagForUnfinishedTopics: Boolean = false

        // ЭТО ПЛОХО, но я  не знаю как переделать
        rawTitles.forEach { title ->
            if(title.text == "<промежуточный текст>" && title.time.toInt() == 0 && title.sources == "<промежуточный текст>"){
                titles.add(Topics(title.title,db.dbUnpack(title.links).map { id -> id.toLong() }))
//                db.delTitle(title.id)
                flagForUnfinishedTopics = true
            }
        }
        println(titles)
        if(!flagForUnfinishedTopics){
            extractTopics(maxTopics, messageSeconds)
            rawTitles = db.getTitles()
            titles = mutableListOf<Topics>()
            rawTitles.forEach { title ->
                if(title.text == "<промежуточный текст>" && title.time.toInt() == 0 && title.sources == "<промежуточный текст>"){
                    titles.add(Topics(title.title,db.dbUnpack(title.links).map { id -> id.toLong() }))
//                    db.delTitle(title.id)
                }
            }
        }

        if (titles.isEmpty()) readyFunc()
        when (titles.size) {
            0 -> readyFunc()
            in maxTopics + 1..Int.MAX_VALUE -> {
                db.titlesTimeKill(0)
                readyFunc()
            }
            else -> {
                titles.forEach { title ->
                    println(title)
                    // Составление списка новостей для нейронки
                    var suitableMessages: List<Message> = mutableListOf()
                    title.ids?.forEach { id ->
                        messages.forEach { message ->
                            if (message.id == id){
                                suitableMessages += message
                            }

                        }
                    }
                    val newsText = suitableMessages.joinToString("\n") { "— ${it.mess}" }
                    val bannedNews = "Таких тем нет" //db.getBannedTopics() и обработка
                    val prompt = """
                Составь резюме по теме: "${title.title}".
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
                                
                Новости:
                $newsText
            """.trimIndent()

                    val response = llm.sendPrompt(prompt) ?: return@forEach

                    // Приведение ответа к адекватному виду
                    val cleanResponse = response.trim().replace("```json", "")
                        .replace("```", "")
                        .replace("\"\"\"", "")
                        .trim()

                    // Парсинг ответа по полям
                    val obj = JSONObject(cleanResponse)
                    val summary = obj.getString("summary")
                    val ids = title.ids ?: return@forEach

                    // время для темы выбирается по первой новости по этой теме или по системному времени, если ошибка
                    var time: Long = System.currentTimeMillis()
                    ids.forEach { id -> time = min(db.getMessage(id)?.time ?: Long.MAX_VALUE, time) }

                    // линки и сурсы в особый формат по id
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
                    // Сохраняем в БД
                    db.addTitle(
                        titleTime = time,
                        title = title.title,
                        text = summary,
                        sources = db.dbPack(*sources.toTypedArray()),
                        links = db.dbPack(*links.toTypedArray())
                    )

                    // задержка перед следующим запросом
                    delay(delaySeconds * 100)
                }
                val count = db.getTitles().filter { it.text == "<промежуточный текст>" }.size
                if (count == 0) readyFunc()
            }
        }
    }



    data class Topics (
        val title: String,
        val ids: List<Long>?
    )
}
