package com.rds.mews.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.math.max
import kotlin.math.min

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources")
    fun getAllSourcesFlow(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun getSourceById(id: Long): SourceEntity?

    @Query("SELECT * FROM sources WHERE feed_url = :feedUrl")
    suspend fun getSourceByUrl(feedUrl: String): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(source: SourceEntity): Long

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE sources SET custom_name = :newName WHERE id = :id")
    suspend fun updateCustomName(id: Long, newName: String)

    @Query("UPDATE sources SET err_count = err_count + 1 WHERE id = :sourceId")
    suspend fun incrementErrorCount(sourceId: Long)

    @Query("UPDATE sources SET err_count = 0 WHERE id = :sourceId")
    suspend fun resetErrorCount(sourceId: Long)

    @Query("UPDATE sources SET err_count = 0")
    suspend fun resetAllErrorCounts()

    @Query("UPDATE sources SET last_sync_time = :syncTime WHERE id = :sourceId")
    suspend fun updateLastSyncTime(sourceId: Long, syncTime: Long)

    @Query("UPDATE sources SET etag_hash = :etag WHERE id = :id")
    suspend fun updateEtag(id: Long, etag: String?)

    @Query("UPDATE sources SET summarizing_last_sync = last_sync_time WHERE id = :sourceId")
    suspend fun updateSummarizingSyncToLastSync(sourceId: Long)

    @Query("UPDATE sources SET summarizing_last_sync = last_sync_time")
    suspend fun updateAllSummarizingSyncToLastSync()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY pub_time DESC")
    fun getAllMessagesFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE pub_time > :timeMs ORDER BY pub_time ASC")
    fun getMessagesAfterTimeFlow(timeMs: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE link = :link")
    suspend fun getMessageByLink(link: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE pub_time > :timeMs ORDER BY pub_time ASC")
    suspend fun getMessagesAfterTimeOneShot(timeMs: Long): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY pub_time ASC")
    suspend fun getAllMessagesOneShot(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE pub_time > :timeMs AND is_duplicate = 0 ORDER BY pub_time ASC")
    suspend fun getUniqueMessagesAfterTimeOneShot(timeMs: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE is_duplicate = 0 ORDER BY pub_time ASC")
    suspend fun getAllUniqueMessagesOneShot(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id IN (:ids)")
    suspend fun getMessagesByIds(ids: List<Long>): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE link IN (:links)")
    suspend fun getMessagesByLinks(links: List<String>): List<MessageEntity>

    @Query("""
        SELECT m.* FROM messages m
        INNER JOIN sources s ON m.source_id = s.id
        WHERE m.is_duplicate = 0 
          AND m.pub_time > COALESCE(s.summarizing_last_sync, :userTimeMs)
        ORDER BY m.pub_time ASC
    """)
    suspend fun getUniqueMessagesForSummarizing(userTimeMs: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>): List<Long>

    @Update
    suspend fun updateAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE pub_time < :timeMs")
    suspend fun deleteBeforeTime(timeMs: Long): Int

    @Query("SELECT MAX(pub_time) FROM messages WHERE source_id = :sourceId")
    suspend fun getMaxPubTimeForSource(sourceId: Long): Long?

    @Query("SELECT clean_text FROM messages WHERE pub_time BETWEEN :timeStart AND :timeEnd")
    suspend fun getCleanTextsInWindow(timeStart: Long, timeEnd: Long): List<String>
}

@Dao
interface TitleDao {
    @Query("SELECT * FROM titles ORDER BY event_time ASC")
    fun getAllTitlesFlow(): Flow<List<TitleEntity>>

    @Query("""
    SELECT * FROM titles t 
    WHERE NOT EXISTS (
        SELECT 1 FROM title_related_map r 
        WHERE r.title_id_1 = t.id
    )
    ORDER BY t.event_time ASC
    """)
    fun getChildfreeTitlesFlow(): Flow<List<TitleEntity>>

    @Query("SELECT * FROM titles WHERE event_time > :timeMs ORDER BY event_time ASC")
    fun getTitlesAfterTimeFlow(timeMs: Long): Flow<List<TitleEntity>>

    @Query("SELECT * FROM titles WHERE id = :id")
    suspend fun getTitleById(id: Long): TitleEntity?

    @Query("""
        SELECT t.* FROM titles t 
        INNER JOIN title_related_map r ON t.id = r.title_id_2 
        WHERE r.title_id_1 = :currentId LIMIT 1
    """)
    suspend fun getParentTitle(currentId: Long): TitleEntity?

    @Query("""
        SELECT t.* FROM titles t 
        INNER JOIN title_related_map r ON t.id = r.title_id_1 
        WHERE r.title_id_2 = :currentId LIMIT 1
    """)
    suspend fun getChildTitle (currentId: Long): TitleEntity?

    @Query("""
        SELECT m.* FROM messages m 
        INNER JOIN title_message_map map ON m.id = map.message_id 
        WHERE map.title_id = :titleId
    """)
    fun getMessagesForTitleFlow(titleId: Long): Flow<List<MessageEntity>>

    @Query("""
        SELECT s.* FROM sources s
        INNER JOIN messages m ON s.id = m.source_id
        INNER JOIN title_message_map map ON m.id = map.message_id
        WHERE map.title_id = :titleId
    """)
    fun getSourcesForTitleFlow(titleId: Long): Flow<List<SourceEntity>>

    @Query("""
        SELECT t.* FROM titles t 
        INNER JOIN title_related_map r ON t.id = r.title_id_2 
        WHERE r.title_id_1 = :titleId
    """)
    fun getRelatedTitlesAsFirstFlow(titleId: Long): Flow<List<TitleEntity>>

    @Query("""
        SELECT t.* FROM titles t 
        INNER JOIN title_related_map r ON t.id = r.title_id_1 
        WHERE r.title_id_2 = :titleId
    """)
    fun getRelatedTitlesAsSecondFlow(titleId: Long): Flow<List<TitleEntity>>

    @Query("SELECT * FROM titles WHERE status = :processingStatusId")
    suspend fun getTitlesNotProcessing(processingStatusId: Int): List<TitleEntity>

    @Query("SELECT message_id FROM title_message_map WHERE title_id = :titleId")
    suspend fun getMessageIdsForTitle(titleId: Long): List<Long>

    @Query("UPDATE titles SET is_read = :isRead WHERE id = :id")
    suspend fun updateReadStatus(id: Long, isRead: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(title: TitleEntity): Long

    @Update
    suspend fun update(title: TitleEntity)

    @Query("DELETE FROM titles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM titles WHERE event_time < :timeMs")
    suspend fun deleteBeforeTime(timeMs: Long): Int

    @Query("DELETE FROM titles WHERE update_time < :timeMs")
    suspend fun deleteBeforeUpdateTime(timeMs: Long): Int

    @Query("DELETE FROM titles WHERE update_time < :timeMs AND is_read = 1")
    suspend fun deleteReadItemsBeforeUpdateTime(timeMs: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTitleMessageMap(map: TitleMessageMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTitleRelatedMap(map: TitleRelatedMap)

    suspend fun insertRelatedMapSafe(id1: Long, id2: Long) {
        val firstId = min(id1, id2)
        val secondId = max(id1, id2)
        if (firstId != secondId) {
            insertTitleRelatedMap(TitleRelatedMap(firstId, secondId))
        }
    }
}