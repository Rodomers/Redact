package com.rds.mews.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(separator = "\u001F")
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split("\u001F")
    }
}

@Entity(
    tableName = "sources",
    indices = [
        Index(value = ["feed_url"], unique = true),
        Index(value = ["website_url"], unique = true)
    ]
)
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "original_name") val originalName: String,
    @ColumnInfo(name = "custom_name") val customName: String? = null,
    @ColumnInfo(name = "website_url") val websiteUrl: String,
    @ColumnInfo(name = "feed_url") val feedUrl: String,
    @ColumnInfo(name = "source_type") val sourceType: Int,
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long,
    @ColumnInfo(name = "err_count") val errCount: Int,
    @ColumnInfo(name = "last_err_msg") val lastErrMsg: String? = null,
    @ColumnInfo(name = "etag_hash") val etagHash: String? = null,
    @ColumnInfo(name = "summarizing_last_sync") val summarizingLastSync: Long? = null
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["link"], unique = true),
        Index(value = ["source_id"]),
        Index(value = ["pub_time"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    val link: String,
    @ColumnInfo(name = "pub_time") val pubTime: Long,
    val title: String,
    @ColumnInfo(name = "original_text") val originalText: String,
    @ColumnInfo(name = "clean_text") val cleanText: String,
    @ColumnInfo(name = "is_duplicate") val isDuplicate: Boolean,
    @ColumnInfo(name = "is_read") val isRead: Boolean,
    @ColumnInfo(name = "fact_check") val factCheck: String?,
    @ColumnInfo(name = "media_urls") val mediaUrls: List<String> = emptyList()
)

@Entity(
    tableName = "titles",
    indices = [
        Index(value = ["event_time"])
    ]
)
data class TitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val summary: String,
    @ColumnInfo(name = "event_time") val eventTime: Long,
    @ColumnInfo(name = "update_time") val updateTime: Long,
    val status: Int,
    @ColumnInfo(name = "is_read") val isRead: Boolean,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean,
    @ColumnInfo(name = "importance_weight") val importanceWeight: Int,
    val keywords: List<String>
)

@Entity(
    tableName = "title_message_map",
    primaryKeys = ["title_id", "message_id"],
    foreignKeys = [
        ForeignKey(
            entity = TitleEntity::class,
            parentColumns = ["id"],
            childColumns = ["title_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["title_id"]),
        Index(value = ["message_id"])
    ]
)
data class TitleMessageMap(
    @ColumnInfo(name = "title_id") val titleId: Long,
    @ColumnInfo(name = "message_id") val messageId: Long
)

@Entity(
    tableName = "title_related_map",
    primaryKeys = ["title_id_1", "title_id_2"],
    foreignKeys = [
        ForeignKey(
            entity = TitleEntity::class,
            parentColumns = ["id"],
            childColumns = ["title_id_1"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TitleEntity::class,
            parentColumns = ["id"],
            childColumns = ["title_id_2"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["title_id_1"]),
        Index(value = ["title_id_2"])
    ]
)
data class TitleRelatedMap(
    @ColumnInfo(name = "title_id_1") val titleId1: Long,
    @ColumnInfo(name = "title_id_2") val titleId2: Long
)