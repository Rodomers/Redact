package com.rds.mews.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rds.mews.localcore.defineSourceType

@Database(
    entities = [
        SourceEntity::class,
        MessageEntity::class,
        TitleEntity::class,
        TitleMessageMap::class,
        TitleRelatedMap::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sourceDao(): SourceDao
    abstract fun messageDao(): MessageDao
    abstract fun titleDao(): TitleDao

    suspend fun insertBatchAndUpdateSourceTime(messages: List<MessageEntity>, sourceId: Long, syncTime: Long) {
        withTransaction {
            val msgDao = messageDao()
            val rowIds = msgDao.insertAll(messages)

            val conflictingLinks = mutableListOf<String>()
            val conflictMap = mutableMapOf<String, MessageEntity>()

            for (i in rowIds.indices) {
                if (rowIds[i] == -1L) {
                    val msg = messages[i]
                    conflictingLinks.add(msg.link)
                    conflictMap[msg.link] = msg
                }
            }

            if (conflictingLinks.isNotEmpty()) {
                val existingMessages = msgDao.getMessagesByLinks(conflictingLinks)
                val toUpdate = mutableListOf<MessageEntity>()

                for (existing in existingMessages) {
                    val newMsg = conflictMap[existing.link]
                    if (newMsg != null && existing.originalText != newMsg.originalText) {
                        toUpdate.add(existing.copy(
                            originalText = newMsg.originalText,
                            cleanText = newMsg.cleanText
                        ))
                    }
                }

                if (toUpdate.isNotEmpty()) {
                    msgDao.updateAll(toUpdate)
                }
            }

            sourceDao().updateLastSyncTime(sourceId, syncTime)
        }
    }

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `original_name` TEXT NOT NULL, `custom_name` TEXT, `website_url` TEXT NOT NULL, `feed_url` TEXT NOT NULL, `source_type` INTEGER NOT NULL, `last_sync_time` INTEGER NOT NULL, `err_count` INTEGER NOT NULL, `last_err_msg` TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sources_feed_url` ON `sources` (`feed_url`)")

                db.execSQL("INSERT INTO `sources` (`id`, `original_name`, `website_url`, `feed_url`, `source_type`, `last_sync_time`, `err_count`) SELECT id, source, link, link, 0, 0, 0 FROM `rss`")
                db.execSQL("DROP TABLE `rss`")

                db.execSQL("CREATE TABLE IF NOT EXISTS `messages_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `source_id` INTEGER NOT NULL, `link` TEXT NOT NULL, `pub_time` INTEGER NOT NULL, `title` TEXT NOT NULL, `original_text` TEXT NOT NULL, `clean_text` TEXT NOT NULL, `is_duplicate` INTEGER NOT NULL, `is_read` INTEGER NOT NULL, `fact_check` TEXT, FOREIGN KEY(`source_id`) REFERENCES `sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")

                db.execSQL("""
                    INSERT INTO `messages_new` (`id`, `source_id`, `link`, `pub_time`, `title`, `original_text`, `clean_text`, `is_duplicate`, `is_read`)
                    SELECT MIN(m.id), s.id, m.link, MAX(m.time), '', m.message, m.message, 0, 0
                    FROM `messages` m
                    INNER JOIN `sources` s ON m.source = s.original_name
                    GROUP BY m.link
                """.trimIndent())

                val oldToNewMsgId = mutableMapOf<Long, Long>()
                db.query("SELECT m.id, mn.id FROM `messages` m INNER JOIN `messages_new` mn ON m.link = mn.link").use { c ->
                    while (c.moveToNext()) {
                        oldToNewMsgId[c.getLong(0)] = c.getLong(1)
                    }
                }

                db.execSQL("DROP TABLE `messages`")
                db.execSQL("ALTER TABLE `messages_new` RENAME TO `messages`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_link` ON `messages` (`link`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_source_id` ON `messages` (`source_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_pub_time` ON `messages` (`pub_time`)")

                db.execSQL("ALTER TABLE `titles` RENAME TO `titles_old`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `titles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `summary` TEXT NOT NULL, `event_time` INTEGER NOT NULL, `update_time` INTEGER NOT NULL, `status` INTEGER NOT NULL, `is_read` INTEGER NOT NULL, `is_pinned` INTEGER NOT NULL, `importance_weight` INTEGER NOT NULL, `keywords` TEXT NOT NULL)")

                db.execSQL("""
                    INSERT INTO `titles` (`id`, `title`, `summary`, `event_time`, `update_time`, `status`, `is_read`, `is_pinned`, `importance_weight`, `keywords`)
                    SELECT id, title, text, time, time, 0, 0, 0, 0, ''
                    FROM `titles_old`
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_titles_event_time` ON `titles` (`event_time`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `title_message_map` (`title_id` INTEGER NOT NULL, `message_id` INTEGER NOT NULL, PRIMARY KEY(`title_id`, `message_id`), FOREIGN KEY(`title_id`) REFERENCES `titles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`message_id`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_title_message_map_title_id` ON `title_message_map` (`title_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_title_message_map_message_id` ON `title_message_map` (`message_id`)")

                db.beginTransaction()
                try {
                    val stmt = db.compileStatement("INSERT OR IGNORE INTO `title_message_map` (`title_id`, `message_id`) VALUES (?, ?)")
                    db.query("SELECT id, messages FROM `titles_old`").use { cursor ->
                        val idIndex = cursor.getColumnIndex("id")
                        val messagesIndex = cursor.getColumnIndex("messages")

                        if (idIndex != -1 && messagesIndex != -1) {
                            while (cursor.moveToNext()) {
                                val titleId = cursor.getLong(idIndex)
                                val packedMessages = cursor.getString(messagesIndex) ?: ""

                                val messageIds = packedMessages.split(",")
                                    .mapNotNull { it.trim().toLongOrNull() }

                                for (oldMsgId in messageIds) {
                                    val safeNewMsgId = oldToNewMsgId[oldMsgId]
                                    if (safeNewMsgId != null) {
                                        stmt.bindLong(1, titleId)
                                        stmt.bindLong(2, safeNewMsgId)
                                        stmt.executeInsert()
                                    }
                                }
                            }
                        }
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }

                db.execSQL("DROP TABLE `titles_old`")

                db.execSQL("CREATE TABLE IF NOT EXISTS `title_related_map` (`title_id_1` INTEGER NOT NULL, `title_id_2` INTEGER NOT NULL, PRIMARY KEY(`title_id_1`, `title_id_2`), FOREIGN KEY(`title_id_1`) REFERENCES `titles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`title_id_2`) REFERENCES `titles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_title_related_map_title_id_1` ON `title_related_map` (`title_id_1`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_title_related_map_title_id_2` ON `title_related_map` (`title_id_2`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sources` ADD COLUMN `etag_hash` TEXT")

                db.query("SELECT id, feed_url FROM `sources`").use { cursor ->
                    val idIndex = cursor.getColumnIndex("id")
                    val urlIndex = cursor.getColumnIndex("feed_url")

                    if (idIndex != -1 && urlIndex != -1) {
                        val updateStmt = db.compileStatement("UPDATE `sources` SET `source_type` = ? WHERE `id` = ?")

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idIndex)
                            val url = cursor.getString(urlIndex) ?: ""

                            val sourceType = defineSourceType(url).id.toLong()

                            updateStmt.bindLong(1, sourceType)
                            updateStmt.bindLong(2, id)
                            updateStmt.executeUpdateDelete()
                        }
                    }
                }
            }
        }
    }
}