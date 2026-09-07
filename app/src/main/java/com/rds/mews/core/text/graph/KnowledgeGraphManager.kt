package com.rds.mews.core.text.graph

import com.rds.mews.core.text.TextComparator
import com.rds.mews.database.keyword_stats.KnowledgeGraphEntity
import com.rds.mews.database.keyword_stats.TermAliasEntity
import com.rds.mews.repositories.KeywordStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

private data class TermCluster(
    var canonicalWord: String,
    val aliases: MutableSet<String> = mutableSetOf(),
    val topicIndices: MutableSet<Int> = mutableSetOf()
)

object GraphCache {
    private val edgeCache = ConcurrentHashMap<Pair<String, String>, Double>()

    private class OptionalAlias(val value: TermAliasEntity?)
    private val aliasCache = ConcurrentHashMap<String, OptionalAlias>()

    private val relatedEntitiesCache = ConcurrentHashMap<String, Set<String>>()

    suspend fun getRelatedEntities(keyword: String, threshold: Double = 0.3): Set<String> {
        val key = keyword.lowercase()
        return relatedEntitiesCache.getOrPut(key) {
            KeywordStatsRepository.getRelatedEntities(key, threshold)
        }
    }

    suspend fun getEdgeWeight(nodeA: String, nodeB: String): Double {
        val a = nodeA.lowercase()
        val b = nodeB.lowercase()
        val key = if (a < b) Pair(a, b) else Pair(b, a)
        return edgeCache.getOrPut(key) {
            KeywordStatsRepository.getEdgeWeight(a, b)
        }
    }

    suspend fun findAlias(term: String): TermAliasEntity? {
        val key = term.lowercase()
        return aliasCache.getOrPut(key) {
            OptionalAlias(KeywordStatsRepository.findAlias(key))
        }.value
    }

    fun clear() {
        edgeCache.clear()
        aliasCache.clear()
        relatedEntitiesCache.clear()
    }


}

object KnowledgeGraphManager {
    private const val DEFAULT_DECAY_FACTOR = 0.98
    private const val DEFAULT_CUTOFF_THRESHOLD = 0.5
    private const val DEFAULT_EXPANSION_THRESHOLD = 0.3
    private val repository = KeywordStatsRepository

    fun countCouplingWeight(
        countA: Int,
        countB: Int,
        coOccurrenceCount: Int
    ): Double {
        if (countA <= 0 || countB <= 0 || coOccurrenceCount <= 0) return 0.0
        val denominator = sqrt(countA.toDouble() * countB.toDouble())
        if (denominator == 0.0) return 0.0
        return (coOccurrenceCount.toDouble() / denominator).coerceIn(0.0, 1.0)
    }

    suspend fun expandContext(
        initialKeywords: Set<String>,
        threshold: Double = DEFAULT_EXPANSION_THRESHOLD
    ): Set<String> {
        if (initialKeywords.isEmpty()) return initialKeywords

        val expandedContext = mutableSetOf<String>()
        for (keyword in initialKeywords) {
            expandedContext += repository.getRelatedEntities(keyword, threshold)
        }
        return expandedContext
    }

    fun applyDailyDecay(
        decayFactor: Double = DEFAULT_DECAY_FACTOR,
        cutoff: Double = DEFAULT_CUTOFF_THRESHOLD
    ) {
        repository.applyDailyDecay(decayFactor, cutoff)
    }

    suspend fun processTopicsAndBuildGraph(
        topicEntitySets: List<Set<String>>,
        currentTimeMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        if (topicEntitySets.isEmpty()) return@withContext

        val termTopicMap = mutableMapOf<String, MutableSet<Int>>()
        topicEntitySets.forEachIndexed { topicIdx, entities ->
            entities.forEach { rawEntity ->
                val cleaned = rawEntity.trim()
                if (cleaned.isNotEmpty()) {
                    termTopicMap.getOrPut(cleaned) { mutableSetOf() }.add(topicIdx)
                }
            }
        }

        if (termTopicMap.isEmpty()) return@withContext


        val sortedTerms = termTopicMap.keys.sortedByDescending { it.length }
        val clusters = mutableListOf<TermCluster>()

        for (term in sortedTerms) {
            val topicSet = termTopicMap[term] ?: continue
            val termLower = term.lowercase()

            val dbAlias = repository.findAlias(termLower)
            val dbTarget = dbAlias?.entityTarget ?: dbAlias?.keywordTarget

            val dbKnownAliases = if (dbTarget != null && dbAlias != null) {
                repository.getStringAliases(
                    entityName = dbAlias.entityTarget,
                    keyword = dbAlias.keywordTarget
                )
            } else {
                emptyList()
            }

            var matchedCluster: TermCluster? = null

            val termCandidates = (setOf(term, term.lowercase()) + setOfNotNull(dbTarget) + dbKnownAliases).filter { it.isNotBlank() }

            if (dbTarget != null) {
                matchedCluster = clusters.find { cluster ->
                    cluster.canonicalWord.equals(dbTarget, ignoreCase = true) ||
                            cluster.aliases.any { it.equals(dbTarget, ignoreCase = true) } ||
                            dbKnownAliases.any { dbAlias -> cluster.aliases.any { it.equals(dbAlias, ignoreCase = true) } }
                }
            }

            val otherClusters = clusters.filter { it != matchedCluster }.toList()
            for (cluster in otherClusters) {
                val clusterCandidates = setOf(cluster.canonicalWord) + cluster.aliases
                val isSimilar = termCandidates.any { candidate1 ->
                    clusterCandidates.any { candidate2 ->
                        TextComparator.areSimilar(candidate1, candidate2, 0.8f)
                    }
                }

                if (isSimilar) {
                    if (matchedCluster == null) {
                        matchedCluster = cluster
                    } else {
                        matchedCluster.topicIndices.addAll(cluster.topicIndices)
                        matchedCluster.aliases.addAll(cluster.aliases)
                        clusters.remove(cluster)
                    }
                }
            }

            if (matchedCluster != null) {
                matchedCluster.topicIndices.addAll(topicSet)
                matchedCluster.aliases.add(term)
                if (dbTarget != null) {
                    matchedCluster.aliases.add(dbTarget)
                    matchedCluster.aliases.addAll(dbKnownAliases)
                }

                val shortestVariant = matchedCluster.aliases.minByOrNull { it.length } ?: term
                if (shortestVariant.length < matchedCluster.canonicalWord.length) {
                    matchedCluster.canonicalWord = shortestVariant
                }
            } else {
                val initialAliases = mutableSetOf(term)
                if (dbTarget != null) {
                    initialAliases.add(dbTarget)
                    initialAliases.addAll(dbKnownAliases)
                }

                val canonical = dbTarget ?: initialAliases.minByOrNull { it.length } ?: term

                clusters.add(
                    TermCluster(
                        canonicalWord = canonical,
                        topicIndices = topicSet.toMutableSet(),
                        aliases = initialAliases
                    )
                )
            }
        }


        val validClusters = mutableListOf<TermCluster>()
        val wordCounts = mutableMapOf<String, Double>()
        val entityClusters = mutableSetOf<String>()

        for (cluster in clusters) {
            val canonical = cluster.canonicalWord
            val dbAlias = repository.findAlias(canonical)

            val isKnownEntity = repository.isKnownEntity(canonical)
            val hasValidAlias = dbAlias != null

            if (isKnownEntity || hasValidAlias || cluster.topicIndices.size > 1) {
                validClusters.add(cluster)

                if (isKnownEntity) {
                    entityClusters.add(canonical)
                } else {
                    wordCounts[canonical] = cluster.topicIndices.size.toDouble()
                }
            }
        }

        if (wordCounts.isNotEmpty()) {
            repository.updateWordStats(wordCounts)
        }

        val canonicalTopicSets = List(topicEntitySets.size) { mutableSetOf<String>() }
        val aliasEntitiesToUpsert = mutableListOf<TermAliasEntity>()
        for (cluster in validClusters) {
            val canonicalWord = cluster.canonicalWord

            val isEntity = entityClusters.contains(canonicalWord)
            for (alias in cluster.aliases) {
                aliasEntitiesToUpsert.add(
                    TermAliasEntity(
                        alias = alias.lowercase(),
                        entityTarget = if (isEntity) canonicalWord else null,
                        keywordTarget = if (isEntity) null else canonicalWord
                    )
                )
            }

            for (topicIndex in cluster.topicIndices) {
                if (topicIndex in canonicalTopicSets.indices) {
                    canonicalTopicSets[topicIndex].add(canonicalWord)
                }
            }
        }
        repository.upsertAliases(aliasEntitiesToUpsert)

        val pairCoOccurrence = mutableMapOf<Pair<String, String>, Int>()
        val singleTermFrequency = mutableMapOf<String, Int>()

        for (topicSet in canonicalTopicSets) {
            val list = topicSet.toList()
            for (i in list.indices) {
                val node1 = list[i]
                singleTermFrequency[node1] = (singleTermFrequency[node1] ?: 0) + 1

                for (j in i + 1 until list.size) {
                    val node2 = list[j]
                    val pair = if (node1 < node2) node1 to node2 else node2 to node1
                    pairCoOccurrence[pair] = (pairCoOccurrence[pair] ?: 0) + 1
                }
            }
        }

        val edgesToUpsert = mutableListOf<KnowledgeGraphEntity>()
        val cutoffThreshold = DEFAULT_CUTOFF_THRESHOLD

        for ((pair, coCount) in pairCoOccurrence) {
            val freqA = singleTermFrequency[pair.first] ?: coCount
            val freqB = singleTermFrequency[pair.second] ?: coCount

            val weight = countCouplingWeight(freqA, freqB, coCount)

            if (weight >= cutoffThreshold) {
                edgesToUpsert.add(
                    KnowledgeGraphEntity(
                        nodeA = pair.first,
                        nodeB = pair.second,
                        weight = weight,
                        lastUpdated = currentTimeMs
                    )
                )
            }
        }

        if (edgesToUpsert.isNotEmpty()) {
            repository.upsertGraphEdges(edgesToUpsert)
        }
    }
}