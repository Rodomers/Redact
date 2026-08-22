package com.rds.mews.database.keyword_stats

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        KeywordStatEntity::class,
        EntityDictionaryEntity::class,
        KnowledgeGraphEntity::class,
        TermAliasEntity::class,
        BurstClusterEntity::class
    ],
    version = 2,
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

        val MIGRATION_1_2 = object : Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `knowledge_graph_temp` (
                `nodeA` TEXT NOT NULL,
                `nodeB` TEXT NOT NULL,
                `weight` REAL NOT NULL,
                `last_updated` INTEGER NOT NULL,
                PRIMARY KEY(`nodeA`, `nodeB`)
            )
            """.trimIndent()
                )

                db.execSQL(
                    """
            INSERT INTO `knowledge_graph_temp` (`nodeA`, `nodeB`, `weight`, `last_updated`)
            SELECT `nodeA`, `nodeB`, `weight`, `last_updated` FROM `knowledge_graph`
            """.trimIndent()
                )

                db.execSQL("DROP TABLE `knowledge_graph`")

                db.execSQL("ALTER TABLE `knowledge_graph_temp` RENAME TO `knowledge_graph`")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_graph_nodeA` ON `knowledge_graph` (`nodeA`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_graph_nodeB` ON `knowledge_graph` (`nodeB`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_graph_weight` ON `knowledge_graph` (`weight`)")
            }
        }
    }
}