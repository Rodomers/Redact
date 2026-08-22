package com.rds.mews.database.keyword_stats

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString("\u0001")

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("\u0001")

    @TypeConverter
    fun fromLongList(value: List<Long>): String = value.joinToString(",")

    @TypeConverter
    fun toLongList(value: String): List<Long> =
        if (value.isEmpty()) emptyList() else value.split(",").mapNotNull { it.toLongOrNull() }
}

@Entity(tableName = "keyword_stats")
data class KeywordStatEntity(
    @PrimaryKey val word: String,
    @ColumnInfo(name = "frequency") val frequency: Double,
    @ColumnInfo(name = "historical_mean") val historicalMean: Double,
    @ColumnInfo(name = "historical_var") val historicalVar: Double,
    @ColumnInfo(name = "last_seen") val lastSeen: Long,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long
)

@Entity(tableName = "entity_dictionary")
data class EntityDictionaryEntity(
    @PrimaryKey
    @ColumnInfo(name = "entity_name") val entityName: String,

    @ColumnInfo(name = "category") val category: String,

    @ColumnInfo(name = "frequency") val frequency: Double,

    @ColumnInfo(name = "last_seen") val lastSeen: Long,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long
)

@Entity(
    tableName = "knowledge_graph",
    primaryKeys = ["nodeA", "nodeB"],
    indices = [
        Index(value = ["nodeA"]),
        Index(value = ["nodeB"]),
        Index(value = ["weight"])
    ]
)
data class KnowledgeGraphEntity(
    @ColumnInfo(name = "nodeA") val nodeA: String,
    @ColumnInfo(name = "nodeB") val nodeB: String,
    @ColumnInfo(name = "weight") val weight: Double,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long
)

@Entity(
    tableName = "term_aliases",
    foreignKeys = [
        ForeignKey(
            entity = EntityDictionaryEntity::class,
            parentColumns = ["entity_name"],
            childColumns = ["entity_target"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = KeywordStatEntity::class,
            parentColumns = ["word"],
            childColumns = ["keyword_target"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["entity_target"]),
        Index(value = ["keyword_target"])
    ]
)
data class TermAliasEntity(
    @PrimaryKey
    @ColumnInfo(name = "alias") val alias: String,
    @ColumnInfo(name = "entity_target") val entityTarget: String? = null,
    @ColumnInfo(name = "keyword_target") val keywordTarget: String? = null
)

@Entity(
    tableName = "burst_clusters",
    indices = [Index(value = ["timestamp"])]
)
data class BurstClusterEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "cluster_id")
    val clusterId: Long = 0,

    @ColumnInfo(name = "keywords")
    val keywords: List<String>,

    @ColumnInfo(name = "message_ids")
    val messageIds: List<Long>,

    @ColumnInfo(name = "peak_z_score")
    val peakZScore: Double,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)