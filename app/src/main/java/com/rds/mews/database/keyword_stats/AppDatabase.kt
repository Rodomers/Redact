package com.rds.mews.database.keyword_stats

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        KeywordStatEntity::class,
        EntityDictionaryEntity::class,
        KnowledgeGraphEntity::class,
        TermAliasEntity::class,
        BurstClusterEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class KeywordsDatabase : RoomDatabase() {
    abstract fun keywordStatsDao(): KeywordStatsDao
    abstract fun entityDictionaryDao(): EntityDictionaryDao
    abstract fun knowledgeGraphDao(): KnowledgeGraphDao
    abstract fun termAliasDao(): TermAliasDao
    abstract fun burstClusterDao(): BurstClusterDao

    companion object {
        const val DATABASE_NAME = "KeywordsDB"
    }
}