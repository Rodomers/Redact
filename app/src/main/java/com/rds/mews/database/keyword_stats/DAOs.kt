package com.rds.mews.database.keyword_stats

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.math.pow

@Dao
interface KeywordStatsDao {
    @Query("SELECT * FROM keyword_stats WHERE word = :word")
    suspend fun getWordStat(word: String): KeywordStatEntity?

    @Query("SELECT * FROM keyword_stats")
    fun getEntities(): Flow<List<KeywordStatEntity>>

    @Query("SELECT * FROM keyword_stats WHERE (:currentTime - last_updated) >= 86400000")
    suspend fun getOutdatedWordStats(currentTime: Long): List<KeywordStatEntity>

    @Upsert
    suspend fun upsertWord(stat: KeywordStatEntity)

    @Upsert
    suspend fun upsertWordsInternal(stats: List<KeywordStatEntity>)

    @Transaction
    suspend fun upsertWords(stats: List<KeywordStatEntity>) {
        if (stats.isEmpty()) return
        stats.chunked(100).forEach { chunk ->
            upsertWordsInternal(chunk)
        }
    }

    @Query("UPDATE keyword_stats SET last_seen = :lastSeenMs WHERE word = :word")
    suspend fun setLastSeen(word: String, lastSeenMs: Long)

    @Query("UPDATE keyword_stats SET frequency = :frequency WHERE word = :word")
    suspend fun setFrequency(word: String, frequency: Double)

    @Query("SELECT COUNT(word) FROM keyword_stats")
    suspend fun countKeywords(): Long

    @Update
    suspend fun update(entity: KeywordStatEntity)

    @Query("DELETE FROM keyword_stats WHERE word = :word")
    suspend fun deleteWord(word: String): Int

    @Query("DELETE FROM keyword_stats WHERE frequency < :epsilon AND last_seen < :timemark")
    suspend fun clearOldWords(timemark: Long, epsilon: Double = 0.05)

    @Query("DELETE FROM keyword_stats")
    suspend fun clearAll()
}

@Dao
interface EntityDictionaryDao {
    @Query("SELECT * FROM entity_dictionary WHERE entity_name = :entityName")
    suspend fun getEntity(entityName: String): EntityDictionaryEntity?

    @Query("SELECT * FROM entity_dictionary WHERE category = :category")
    fun getCategoryEntities(category: String): Flow<List<EntityDictionaryEntity>>

    @Query("SELECT * FROM entity_dictionary")
    fun getEntities(): Flow<List<EntityDictionaryEntity>>

    @Upsert
    suspend fun upsertEntity(entity: EntityDictionaryEntity)

    @Upsert
    suspend fun upsertEntitiesInternal(entities: List<EntityDictionaryEntity>)

    @Transaction
    suspend fun upsertEntities(entities: List<EntityDictionaryEntity>) {
        if (entities.isEmpty()) return
        entities.chunked(100).forEach { chunk ->
            upsertEntitiesInternal(chunk)
        }
    }

    @Query("UPDATE entity_dictionary SET last_seen = :lastSeenMs WHERE entity_name = :entityName")
    suspend fun setLastSeen(entityName: String, lastSeenMs: Long)

    @Query("UPDATE entity_dictionary SET frequency = :frequency WHERE entity_name = :entityName")
    suspend fun setFrequency(entityName: String, frequency: Double)

    @Update
    suspend fun update(entity: EntityDictionaryEntity)

    @Query("SELECT * FROM entity_dictionary WHERE (:currentTime - last_updated) > :oneDayMs")
    suspend fun getOutdatedEntities(currentTime: Long, oneDayMs: Long = 86_400_000): List<EntityDictionaryEntity>

    @Transaction
    suspend fun applyRegularDecay(currentTime: Long, decayFactor: Double = 0.98, cutoff: Double = 0.1) {
        val oneDayMs = 86_400_000.0
        val outdatedEdges = getOutdatedEntities(currentTime)

        if (outdatedEdges.isEmpty()) return

        val updatedEntries = outdatedEdges.map { entity ->
            val daysPassed = ((currentTime - entity.lastUpdated) / oneDayMs).coerceAtLeast(1.0)
            entity.copy(
                frequency = entity.frequency * decayFactor.pow(daysPassed),
                lastUpdated = currentTime
            )
        }

        upsertEntities(updatedEntries)
        deleteWeakEntities(currentTime, cutoff)
    }

    @Query("DELETE FROM entity_dictionary WHERE entity_name = :entityName")
    suspend fun deleteEntity(entityName: String): Int

    @Query("DELETE FROM entity_dictionary WHERE frequency < :cutoff AND last_seen < :timemark")
    suspend fun deleteWeakEntities(timemark: Long, cutoff: Double = 0.1)
}

@Dao
interface KnowledgeGraphDao {
    @Query("SELECT * FROM knowledge_graph WHERE nodeA = :nodeA AND nodeB = :nodeB")
    suspend fun getDirectEdge(nodeA: String, nodeB: String): KnowledgeGraphEntity?

    suspend fun getEdge(nodeA: String, nodeB: String): KnowledgeGraphEntity? {
        val (a, b) = listOf(nodeA, nodeB).sorted()
        return getDirectEdge(a, b)
    }

    @Query("SELECT * FROM knowledge_graph WHERE nodeA = :node OR nodeB = :node")
    suspend fun getEdgesForNode(node: String): List<KnowledgeGraphEntity>

    @Query("SELECT * FROM knowledge_graph WHERE (nodeA = :keyword OR nodeB =:keyword) AND weight >= :threshold")
    suspend fun getRelatedEntities(keyword: String, threshold: Double): List<KnowledgeGraphEntity>

    @Query("SELECT * FROM knowledge_graph WHERE (:currentTime - last_updated) > :oneDayMs")
    suspend fun getOutdatedEdges(currentTime: Long, oneDayMs: Long = 86_400_000): List<KnowledgeGraphEntity>

    @Query("SELECT * FROM knowledge_graph")
    fun getEntities(): Flow<List<KnowledgeGraphEntity>>

    @Upsert
    suspend fun upsertRawEdge(edge: KnowledgeGraphEntity)

    @Upsert
    suspend fun upsertRawEdgesInternal(edges: List<KnowledgeGraphEntity>)

    @Transaction
    suspend fun upsertRawEdges(edges: List<KnowledgeGraphEntity>) {
        if (edges.isEmpty()) return
        edges.chunked(100).forEach { chunk ->
            upsertRawEdgesInternal(chunk)
        }
    }

    @Transaction
    suspend fun upsertEdge(edge: KnowledgeGraphEntity) {
        val (a, b) = listOf(edge.nodeA, edge.nodeB).sorted()
        upsertRawEdge(edge.copy(nodeA = a, nodeB = b))
    }

    @Transaction
    suspend fun upsertEdges(edges: List<KnowledgeGraphEntity>) {
        upsertRawEdges(
            edges.map { edge ->
                val (a, b) = listOf(edge.nodeA, edge.nodeB).sorted()
                edge.copy(nodeA = a, nodeB = b)
            }
        )
    }

    @Query("UPDATE knowledge_graph SET last_updated = :lastUpdatedMs WHERE (nodeA = :nodeA AND nodeB = :nodeB) OR (nodeB = :nodeA AND nodeA = :nodeB)")
    suspend fun setLastUpdated(nodeA: String, nodeB: String, lastUpdatedMs: Long): Int

    @Query("DELETE FROM knowledge_graph WHERE weight < :cutoff")
    suspend fun deleteWeakEdges(cutoff: Double = 0.1)

    @Transaction
    suspend fun applyRegularDecay(currentTime: Long, decayFactor: Double = 0.98, cutoff: Double = 0.1) {
        val oneDayMs = 86_400_000.0
        val outdatedEdges = getOutdatedEdges(currentTime)

        if (outdatedEdges.isEmpty()) return

        val updatedEntries = outdatedEdges.map { edge ->
            val daysPassed = ((currentTime - edge.lastUpdated) / oneDayMs).coerceAtLeast(1.0)
            edge.copy(
                weight = edge.weight * decayFactor.pow(daysPassed),
                lastUpdated = currentTime
            )
        }

        upsertRawEdges(updatedEntries)
        deleteWeakEdges(cutoff)
    }

    @Update
    suspend fun updateEdge(entity: KnowledgeGraphEntity)

    @Update
    suspend fun updateEdgesInternal(entities: List<KnowledgeGraphEntity>)

    @Transaction
    suspend fun updateEdges(entities: List<KnowledgeGraphEntity>) {
        if (entities.isEmpty()) return
        entities.chunked(100).forEach { chunk ->
            updateEdgesInternal(chunk)
        }
    }

    @Query("DELETE FROM knowledge_graph WHERE (nodeA = :nodeA AND nodeB = :nodeB) OR (nodeB = :nodeA AND nodeA = :nodeB)")
    suspend fun delEdge(nodeA: String, nodeB: String): Int

    @Query("""
        DELETE FROM knowledge_graph 
        WHERE nodeA NOT IN (SELECT entity_name FROM entity_dictionary UNION SELECT word FROM keyword_stats) 
           OR nodeB NOT IN (SELECT entity_name FROM entity_dictionary UNION SELECT word FROM keyword_stats)
    """)
    suspend fun deleteDanglingEdges()
}

@Dao
interface TermAliasDao {

    @Query("SELECT * FROM term_aliases WHERE alias = :alias LIMIT 1")
    suspend fun findAlias(alias: String): TermAliasEntity?

    @Query("SELECT * FROM term_aliases WHERE entity_target = :entityName")
    suspend fun getAliasesForEntity(entityName: String): List<TermAliasEntity>

    @Query("SELECT * FROM term_aliases WHERE keyword_target = :word")
    suspend fun getAliasesForKeyword(word: String): List<TermAliasEntity>

    @Transaction
    suspend fun getStringAliases(entityName: String? = null, keyword: String? = null): List<String> {
        return when {
            entityName != null -> getAliasesForEntity(entityName).map { it.alias }
            keyword != null -> getAliasesForKeyword(keyword).map { it.alias }
            else -> emptyList()
        }
    }

    @Upsert
    suspend fun upsertAlias(alias: TermAliasEntity)

    @Upsert
    suspend fun upsertAliasesInternal(aliases: List<TermAliasEntity>)

    @Transaction
    suspend fun upsertAliases(aliases: List<TermAliasEntity>) {
        if (aliases.isEmpty()) return
        aliases.chunked(100).forEach { chunk ->
            upsertAliasesInternal(chunk)
        }
    }

    @Query("DELETE FROM term_aliases WHERE alias = :alias")
    suspend fun deleteAlias(alias: String): Int
}

@Dao
interface BurstClusterDao {
    @Query("SELECT * FROM burst_clusters WHERE cluster_id = :clusterId")
    suspend fun getCluster(clusterId: Long): BurstClusterEntity?

    @Query("SELECT * FROM burst_clusters ORDER BY timestamp DESC")
    fun getAllClusters(): Flow<List<BurstClusterEntity>>

    @Query("SELECT * FROM burst_clusters WHERE timestamp >= :sinceTimestamp")
    suspend fun getActiveClusters(sinceTimestamp: Long): List<BurstClusterEntity>

    @Upsert
    suspend fun upsertCluster(cluster: BurstClusterEntity)

    @Upsert
    suspend fun upsertClustersInternal(clusters: List<BurstClusterEntity>)

    @Transaction
    suspend fun upsertClusters(clusters: List<BurstClusterEntity>) {
        if (clusters.isEmpty()) return
        clusters.chunked(100).forEach { chunk ->
            upsertClustersInternal(chunk)
        }
    }

    @Query("DELETE FROM burst_clusters WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOldClusters(cutoffTimestamp: Long)

    @Query("DELETE FROM burst_clusters")
    suspend fun clearAll()
}