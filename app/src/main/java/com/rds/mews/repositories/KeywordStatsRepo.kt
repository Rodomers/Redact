package com.rds.mews.repositories

import com.rds.mews.database.keyword_stats.KeywordStatEntity
import com.rds.mews.database.keyword_stats.KeywordStatsDao
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rds.mews.database.keyword_stats.BurstClusterDao
import com.rds.mews.database.keyword_stats.BurstClusterEntity
import com.rds.mews.database.keyword_stats.EntityDictionaryDao
import com.rds.mews.database.keyword_stats.EntityDictionaryEntity
import com.rds.mews.database.keyword_stats.KeywordsDatabase
import com.rds.mews.database.keyword_stats.KnowledgeGraphDao
import com.rds.mews.database.keyword_stats.KnowledgeGraphEntity
import com.rds.mews.database.keyword_stats.TermAliasDao
import com.rds.mews.database.keyword_stats.TermAliasEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.pow

enum class EntityCategories(val category: String) {
    PERSONS("persons"),
    LOCATIONS("locations"),
    ORGANIZATIONS("organizations"),
    EVENTS("events"),
    PRODUCTS_TECH("products_tech"),
    LAWS_REGULATIONS("laws_regulations");

    companion object {
        fun fromString(category: String): EntityCategories? {
            return entries.find { it.category == category }
        }
    }
}

object KeywordStatsRepository {
    private lateinit var database: KeywordsDatabase
    private lateinit var externalScope: CoroutineScope
    private lateinit var keywordStatsDao: KeywordStatsDao
    private lateinit var entityDictionaryDao: EntityDictionaryDao
    private lateinit var knowledgeGraphDao: KnowledgeGraphDao
    private lateinit var burstClusterDao: BurstClusterDao
    private lateinit var termAliasDao: TermAliasDao

//    lateinit var keywordStats: Flow<List<KeywordStatEntity>>
//    lateinit var entities: Flow<List<EntityDictionaryEntity>>
//    lateinit var knowledgeGraph: Flow<List<KnowledgeGraphEntity>>

    var isInitialized = false

    private const val DECAY_LAMBDA = 0.95
    private const val DAY_IN_MS = 86_400_000.0
    private const val KNOWLEDGE_WEIGHT_CUTOFF = 0.1

    fun initialize(context: Context, scope: CoroutineScope) {
        if (isInitialized) return

        val appContext = context.applicationContext
        this.externalScope = scope
        this.database = Room.databaseBuilder(appContext, KeywordsDatabase::class.java,
            KeywordsDatabase.DATABASE_NAME)
            .addMigrations(KeywordsDatabase.MIGRATION_1_2)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        this.keywordStatsDao = database.keywordStatsDao()
        this.entityDictionaryDao = database.entityDictionaryDao()
        this.knowledgeGraphDao = database.knowledgeGraphDao()
        this.termAliasDao = database.termAliasDao()
        this.burstClusterDao = database.burstClusterDao()

//        keywordStats = keywordStatsDao.getEntities()
//            .flowOn(Dispatchers.IO)
//        entities = entityDictionaryDao.getEntities()
//            .flowOn(Dispatchers.IO)
//        knowledgeGraph = knowledgeGraphDao.getEntities()
//            .flowOn(Dispatchers.IO)

        isInitialized = true
    }

    suspend fun updateWordStats(wordCounts: Map<String, Double>) {
        if (wordCounts.isEmpty()) return

        val entitiesToUpdate = mutableListOf<KeywordStatEntity>()
        val timestamp = System.currentTimeMillis()

        for ((word, count) in wordCounts) {
            val existing = keywordStatsDao.getWordStat(word)

            if (existing != null) {
                val deltaDays = (timestamp - existing.lastSeen).coerceAtLeast(0L) / DAY_IN_MS
                val decayedFrequency = existing.frequency * DECAY_LAMBDA.pow(deltaDays)
                val newFrequency = decayedFrequency + count

                entitiesToUpdate.add(
                    existing.copy(
                        frequency = newFrequency,
                        lastSeen = timestamp
                    )
                )
            } else {
                entitiesToUpdate.add(
                    KeywordStatEntity(
                        word = word,
                        frequency = count,
                        historicalMean = 0.0,
                        historicalVar = 0.0,
                        lastSeen = timestamp,
                        lastUpdated = timestamp
                    )
                )
            }
        }

        keywordStatsDao.upsertWords(entitiesToUpdate)
    }

    suspend fun updateEntities(entitiesToCategory: Map<String, List<String>>) {
        if (entitiesToCategory.isEmpty()) return

        val entitiesToUpdate = mutableListOf<EntityDictionaryEntity>()
        val timestamp = System.currentTimeMillis()

        for ((strCategory, words) in entitiesToCategory) {
            val category = EntityCategories.fromString(strCategory) ?: continue
            for (word in words) {
                val inAliases = termAliasDao.findAlias(word)
                val existing = when (val entity = entityDictionaryDao.getEntity(word))  {
                    null -> {
                        if (inAliases == null) null
                        else {
                            entityDictionaryDao.getEntity(inAliases.entityTarget ?: "")
                        }
                    }
                    else -> entity
                }

                if (existing != null) {
                    val deltaDays = (timestamp - existing.lastSeen).coerceAtLeast(0L) / DAY_IN_MS
                    val decayedFrequency = existing.frequency * DECAY_LAMBDA.pow(deltaDays)
                    val newFrequency = decayedFrequency + 1.0

                    entitiesToUpdate.add(
                        existing.copy(
                            category = category.name,
                            frequency = newFrequency,
                            lastSeen = timestamp,
                            lastUpdated = timestamp
                        )
                    )
                } else {
                    entitiesToUpdate.add(
                        EntityDictionaryEntity(
                            entityName = word,
                            category = category.name,
                            frequency = 1.0,
                            lastSeen = timestamp,
                            lastUpdated = timestamp
                        )
                    )
                }
            }
        }

        entityDictionaryDao.upsertEntities(entitiesToUpdate)
    }

    suspend fun recalculateHistoricalStats(
        currentTimeMs: Long = System.currentTimeMillis(),
        alpha: Double = 0.1
    ) = withContext(Dispatchers.IO) {
        val outdatedStats = keywordStatsDao.getOutdatedWordStats(currentTimeMs)

        if (outdatedStats.isEmpty()) return@withContext

        val updatedList = outdatedStats.map { stat ->
            val daysPassed = ((currentTimeMs - stat.lastUpdated) / DAY_IN_MS).toInt().coerceAtLeast(0)
            var currentMean = stat.historicalMean
            var currentVar = stat.historicalVar
            val currentFreq = stat.frequency

            repeat(daysPassed) {
                currentMean = (1.0 - alpha) * currentMean + alpha * currentFreq
                val diff = currentFreq - currentMean
                currentVar = (1.0 - alpha) * currentVar + alpha * (diff * diff)
            }

            stat.copy(
                historicalMean = currentMean,
                historicalVar = currentVar,
                lastUpdated = currentTimeMs
            )
        }

        keywordStatsDao.upsertWords(updatedList)
    }

    suspend fun getSmoothedIdf(word: String, totalDocumentsN: Double): Float {
        val stat = keywordStatsDao.getWordStat(word) ?: return calculateIdfFormula(0.0, totalDocumentsN)
        val currentTimeMs = System.currentTimeMillis()

        val deltaDays = (currentTimeMs - stat.lastSeen).coerceAtLeast(0L) / DAY_IN_MS
        val currentDf = stat.frequency * DECAY_LAMBDA.pow(deltaDays)

        return calculateIdfFormula(currentDf, totalDocumentsN)
    }

    suspend fun getWordStat(word: String): KeywordStatEntity? {
        return keywordStatsDao.getWordStat(word)
    }

    suspend fun isKnownEntity(word: String): Boolean {
        return entityDictionaryDao.getEntity(word) != null
    }

    suspend fun countKeywords(): Long {
        return keywordStatsDao.countKeywords()
    }

    suspend fun clearOldKeywords(days: Int = 7) {
        val timeMark = System.currentTimeMillis() - DAY_IN_MS * days
        keywordStatsDao.clearOldWords(timeMark.toLong())
        knowledgeGraphDao.deleteDanglingEdges()
    }

    suspend fun getRelatedEntities(keyword: String, threshold: Double = 0.3): Set<String> {
        val entities = knowledgeGraphDao.getRelatedEntities(keyword, threshold)
        return entities.map { if (it.nodeA != keyword) it.nodeA else it.nodeB }.toSet().plus(keyword)
    }

    suspend fun getEdgeWeight(nodeA: String, nodeB: String): Double {
        if (nodeA == nodeB) return 1.0
        val nodes = setOf(nodeA, nodeB).sorted()
        if (nodes.size != 2) return 0.0

        val edge = knowledgeGraphDao.getEdge(nodes[0], nodes[1])
        return edge?.weight ?: 0.0
    }

    fun applyDailyDecay(decayFactor: Double, cutoff: Double = KNOWLEDGE_WEIGHT_CUTOFF) {
        externalScope.launch(Dispatchers.IO) {
            val currentTime = System.currentTimeMillis()
            knowledgeGraphDao.applyRegularDecay(currentTime, decayFactor, cutoff)
            entityDictionaryDao.applyRegularDecay(currentTime, decayFactor, cutoff)
            knowledgeGraphDao.deleteDanglingEdges()
        }
    }

    fun insertGraphNode(nodeA: String, nodeB: String, weight: Double) {
        if (weight < KNOWLEDGE_WEIGHT_CUTOFF) return

        externalScope.launch(Dispatchers.IO) {
            val nodes = setOf(nodeA, nodeB).sorted()
            if (nodes.size != 2) return@launch

            knowledgeGraphDao.upsertEdge(
                KnowledgeGraphEntity(
                    nodeA = nodes[0],
                    nodeB = nodes[1],
                    weight = weight,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun upsertGraphEdges(edges: List<KnowledgeGraphEntity>) {
        knowledgeGraphDao.upsertEdges(edges)
    }

    suspend fun findAlias(alias: String): TermAliasEntity? {
        return termAliasDao.findAlias(alias)
    }

    suspend fun getStringAliases(entityName: String? = null, keyword: String? = null): List<String> {
        return termAliasDao.getStringAliases(entityName, keyword)
    }

    suspend fun upsertAliases(aliases: List<TermAliasEntity>) {
        termAliasDao.upsertAliases(aliases)
    }

    suspend fun getBurstClusters(sinceTimestamp: Long = 0): List<BurstClusterEntity> {
        return burstClusterDao.getActiveClusters(sinceTimestamp)
    }

    fun saveBurstCluster(cluster: BurstClusterEntity) {
        externalScope.launch(Dispatchers.IO) {
            burstClusterDao.upsertCluster(cluster)
        }
    }

    fun deleteClusters(cutoffTimestamp: Long? = null) {
        externalScope.launch(Dispatchers.IO) {
            when (cutoffTimestamp) {
                null -> burstClusterDao.clearAll()
                else -> burstClusterDao.deleteOldClusters(cutoffTimestamp)
            }
        }
    }

    /**
     *ln((N + 1) / (DF + 1)) + 1
     */
    private fun calculateIdfFormula(df: Double, n: Double): Float {
        val idf = ln((n + 1.0) / (df + 1.0)) + 1.0
        return idf.toFloat()
    }
}