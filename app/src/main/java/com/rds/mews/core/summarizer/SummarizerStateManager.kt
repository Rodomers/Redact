package com.rds.mews.core.summarizer

import android.content.Context
import androidx.core.util.AtomicFile
import com.rds.mews.localcore.UpdatingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
data class SummarizerState(
    val timemark: Long = System.currentTimeMillis(),
    val targetTime: Long = System.currentTimeMillis(),
    val attempt: Int = 0,
    val updatingState: UpdatingState = UpdatingState.DEFAULT,
    val remainingMessageIds: List<Long> = emptyList(),
    val victimMessages: List<Long> = emptyList(),
    val stagedRawTopics: List<RawTopicDto> = emptyList(),
    val primaryTopics: List<RawTopicDto> = emptyList(),
    val reserveTopics: List<RawTopicDto> = emptyList(),
    val blitzTopics: List<RawTopicDto> = emptyList(),
    val currentLanguage: String = "english"
)

@Serializable
data class RawTopicDto(
    val id: Long,
    val title: String,
    val ids: List<Long>,
    val weight: Int,
    val keywords: List<String>,
    val isBlitz: Boolean
)
fun RawTopicDto.toTopics(): NewsSummarizer.Topics {
    return NewsSummarizer.Topics(
        id = this.id,
        title = this.title,
        ids = this.ids,
        weight = this.weight,
        keywords = this.keywords,
        isBlitz = this.isBlitz
    )
}


class SummarizerStateManager(
    context: Context,
    fileName: String = "summarizer_state.json"
) {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    private val file = File(context.filesDir, fileName)
    private val atomicFile = AtomicFile(file)
    private val mutex = Mutex()

    suspend fun readState(): SummarizerState? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readStateInternal()
        }
    }

    suspend fun updateState(transform: (SummarizerState) -> SummarizerState) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentState = try {
                if (file.exists()) {
                    val bytes = atomicFile.readFully()
                    jsonParser.decodeFromString<SummarizerState>(String(bytes, Charsets.UTF_8))
                } else {
                    SummarizerState()
                }
            } catch (_: Exception) {
                SummarizerState()
            }

            val newState = transform(currentState)
            val jsonString = jsonParser.encodeToString(newState)

            var fos: FileOutputStream? = null
            try {
                fos = atomicFile.startWrite()
                fos.write(jsonString.toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(fos)
            } catch (e: Exception) {
                if (fos != null) {
                    atomicFile.failWrite(fos)
                }
            }
        }
    }

    suspend fun clearState() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (file.exists()) {
                atomicFile.delete()
            }
        }
    }

    private fun readStateInternal(): SummarizerState? {
        return try {
            val bytes = atomicFile.readFully()
            val jsonString = String(bytes, Charsets.UTF_8)
            if (jsonString.isBlank()) return null
            jsonParser.decodeFromString<SummarizerState>(jsonString)
        } catch (_: Exception) {
            null
        }
    }
}