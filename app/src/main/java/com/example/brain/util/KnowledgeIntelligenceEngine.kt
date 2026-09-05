package com.example.brain.util

import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.ItemRelationEntity
import com.example.brain.data.entity.SavedItemEntity
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Data model for suggested knowledge relationships.
 */
data class RelationshipCandidate(
    val item: SavedItemEntity,
    val score: Int,
    val suggestedType: String,
    val explanation: String,
    val confidence: Float,
    val reasons: List<String>
)

/**
 * Data model for thematic topics detected deterministically from tags, titles, and projects.
 */
data class DiscoveredTopic(
    val name: String,
    val itemCount: Int,
    val commonTags: List<String>,
    val relatedProjects: List<String>,
    val sampleItems: List<SavedItemEntity>,
    val growthHistory: Map<String, Int> = emptyMap()
)

/**
 * Structured rule for Smart Collections (no raw SQL).
 */
data class SmartCollectionRule(
    val tag: String? = null,
    val contentType: String? = null,
    val folderId: String? = null,
    val isFavorite: Boolean? = null,
    val isReadLater: Boolean? = null,
    val keyword: String? = null
) {
    fun toJson(): String {
        val pairs = mutableListOf<String>()
        tag?.let { pairs.add("\"tag\":\"${escape(it)}\"") }
        contentType?.let { pairs.add("\"contentType\":\"${escape(it)}\"") }
        folderId?.let { pairs.add("\"folderId\":\"${escape(it)}\"") }
        isFavorite?.let { pairs.add("\"isFavorite\":$it") }
        isReadLater?.let { pairs.add("\"isReadLater\":$it") }
        keyword?.let { pairs.add("\"keyword\":\"${escape(it)}\"") }
        return "{" + pairs.joinToString(",") + "}"
    }

    companion object {
        private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

        fun fromJson(jsonStr: String?): SmartCollectionRule {
            if (jsonStr.isNullOrBlank()) return SmartCollectionRule()
            val clean = jsonStr.trim().removeSurrounding("{", "}")
            if (clean.isBlank()) return SmartCollectionRule()

            var tag: String? = null
            var contentType: String? = null
            var folderId: String? = null
            var isFavorite: Boolean? = null
            var isReadLater: Boolean? = null
            var keyword: String? = null

            val regex = Regex(""""(\w+)"\s*:\s*("(?:[^"\\]|\\.)*"|true|false|\b[^\s,}]+\b)""")
            for (match in regex.findAll(clean)) {
                val key = match.groupValues[1]
                val rawVal = match.groupValues[2]
                val strVal = if (rawVal.startsWith("\"") && rawVal.endsWith("\"")) {
                    rawVal.substring(1, rawVal.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                } else null

                when (key) {
                    "tag" -> tag = strVal
                    "contentType" -> contentType = strVal
                    "folderId" -> folderId = strVal
                    "isFavorite" -> isFavorite = rawVal.toBooleanStrictOrNull()
                    "isReadLater" -> isReadLater = rawVal.toBooleanStrictOrNull()
                    "keyword" -> keyword = strVal
                }
            }
            return SmartCollectionRule(tag, contentType, folderId, isFavorite, isReadLater, keyword)
        }
    }

    fun matches(item: SavedItemEntity, itemTags: List<String>): Boolean {
        if (item.isSecret) return false
        if (tag != null && !itemTags.any { it.equals(tag, ignoreCase = true) }) return false
        if (contentType != null && !item.type.equals(contentType, ignoreCase = true)) return false
        if (folderId != null && item.folderId != folderId) return false
        if (isFavorite != null && item.isFavorite != isFavorite) return false
        if (isReadLater != null && item.isReadLater != isReadLater) return false
        if (keyword != null) {
            val kw = keyword.lowercase()
            val text = "${item.title} ${item.description ?: ""} ${item.notes}".lowercase()
            if (!text.contains(kw)) return false
        }
        return true
    }
}

/**
 * Visual Graph models for Item-Centered and Connection Exploration.
 */
data class GraphNode(
    val id: String,
    val label: String,
    val type: String, // "CURRENT", "RELATED", "PROJECT", "TAG"
    val isCenter: Boolean = false,
    val subtitle: String? = null
)

data class GraphEdge(
    val sourceId: String,
    val targetId: String,
    val label: String
)

data class ItemGraphData(
    val centerNode: GraphNode,
    val connectedNodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

/**
 * Canonical Knowledge Intelligence Engine for Second Brain.
 *
 * 100% deterministic, local-only heuristics:
 * - Transparent scoring for related items with explainable reasons.
 * - Directional relationship handling (e.g. References vs Referenced By).
 * - Distinguishes between duplicate detection and relatedness.
 * - Thematic topic extraction with real timeline growth metrics.
 * - Real discovery signals: Forgotten Knowledge, Unexplored, You Keep Collecting.
 * - Strict Vault exclusion on all calculations.
 */
object KnowledgeIntelligenceEngine {

    val SUPPORTED_RELATIONS = listOf(
        "RELATED_TO" to "Related To",
        "REFERENCES" to "References",
        "INSPIRED_BY" to "Inspired By",
        "PART_OF" to "Part Of",
        "DERIVED_FROM" to "Derived From",
        "DUPLICATE_OF" to "Duplicate Of",
        "SUPPORTS" to "Supports",
        "CONTRADICTS" to "Contradicts"
    )

    private val STOP_WORDS = setOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and",
        "any", "are", "aren't", "as", "at", "be", "because", "been", "before", "being",
        "below", "between", "both", "but", "by", "can", "can't", "cannot", "could",
        "did", "do", "does", "doing", "don't", "down", "during", "each", "few", "for",
        "from", "further", "had", "has", "have", "having", "he", "her", "here", "hers",
        "herself", "him", "himself", "his", "how", "i", "if", "in", "into", "is",
        "it", "its", "itself", "just", "me", "more", "most", "my", "myself", "no",
        "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought",
        "our", "ours", "ourselves", "out", "over", "own", "same", "she", "should",
        "so", "some", "such", "than", "that", "the", "their", "theirs", "them",
        "themselves", "then", "there", "these", "they", "this", "those", "through",
        "to", "too", "under", "until", "up", "very", "was", "we", "were", "what",
        "when", "where", "which", "while", "who", "whom", "why", "with", "would",
        "you", "your", "yours", "yourself", "yourselves", "http", "https", "www", "com"
    )

    /**
     * Compute relatedness score and transparent explanation between target item and candidates.
     */
    fun findRelatedCandidates(
        target: SavedItemEntity,
        candidates: List<SavedItemEntity>,
        targetTags: List<String> = emptyList(),
        candidatesTagsMap: Map<String, List<String>> = emptyMap(),
        targetProjectName: String? = null,
        projectsMap: Map<String, String> = emptyMap(),
        dismissedIds: Set<String> = emptySet(),
        minScoreThreshold: Int = 30,
        limit: Int = 5
    ): List<RelationshipCandidate> {
        if (target.isSecret) return emptyList()

        val targetUrl = target.url?.let { UrlNormalizer.normalize(it) }
        val targetDomain = target.url?.let { UrlNormalizer.extractDomain(it) }
        val targetTitleTokens = tokenize(target.title)
        val targetContentTokens = tokenize("${target.notes} ${target.description ?: ""} ${target.extractedText ?: ""}")

        val results = mutableListOf<RelationshipCandidate>()

        for (candidate in candidates) {
            // Strictly exclude secret vault items, identical item, and user-dismissed suggestions
            if (candidate.isSecret || candidate.id == target.id || candidate.id in dismissedIds) {
                continue
            }

            var score = 0
            val reasons = mutableListOf<String>()
            var suggestedType = "RELATED_TO"

            // 1. Strict Duplicate Detection
            val candidateUrl = candidate.url?.let { UrlNormalizer.normalize(it) }
            val isSameUrl = !targetUrl.isNullOrBlank() && !candidateUrl.isNullOrBlank() && targetUrl == candidateUrl
            if (isSameUrl) {
                score = 95
                suggestedType = "DUPLICATE_OF"
                reasons.add("Identical normalized web address")
            } else {
                // 2. Project alignment (+30)
                if (target.folderId.isNotBlank() && target.folderId != "inbox_default_id" && target.folderId == candidate.folderId) {
                    score += 30
                    val projName = targetProjectName ?: projectsMap[target.folderId] ?: "Current Project"
                    reasons.add("Same project '$projName'")
                }

                // 3. Shared tags (+20 each, cap at +40)
                val candidateTags = candidatesTagsMap[candidate.id] ?: emptyList()
                val sharedTags = targetTags.filter { t -> candidateTags.any { it.equals(t, ignoreCase = true) } }
                if (sharedTags.isNotEmpty()) {
                    val tagScore = (sharedTags.size * 20).coerceAtMost(40)
                    score += tagScore
                    val tagPreview = sharedTags.take(3).joinToString(", ") { "#$it" }
                    reasons.add("Shared tags: $tagPreview")
                }

                // 4. Same Web Domain (+15)
                val candidateDomain = candidate.url?.let { UrlNormalizer.extractDomain(it) }
                if (!targetDomain.isNullOrBlank() && !candidateDomain.isNullOrBlank() && targetDomain == candidateDomain) {
                    score += 15
                    reasons.add("Same domain ($targetDomain)")
                }

                // 5. Title token overlap (Jaccard similarity up to +25)
                val candidateTitleTokens = tokenize(candidate.title)
                val titleJaccard = calculateJaccard(targetTitleTokens, candidateTitleTokens)
                if (titleJaccard > 0.2f) {
                    val titlePoints = (titleJaccard * 25).roundToInt().coerceAtMost(25)
                    score += titlePoints
                    reasons.add("Similar title concepts")
                }

                // 6. Extracted text & notes overlap (up to +20)
                val candidateContentTokens = tokenize("${candidate.notes} ${candidate.description ?: ""} ${candidate.extractedText ?: ""}")
                val contentJaccard = calculateJaccard(targetContentTokens, candidateContentTokens)
                if (contentJaccard > 0.15f) {
                    val contentPoints = (contentJaccard * 20).roundToInt().coerceAtMost(20)
                    score += contentPoints
                    reasons.add("Content & terminology overlap")
                }

                // 7. Check directional references heuristics
                val targetTextCombined = "${target.title} ${target.notes}".lowercase()
                val candidateTitleLower = candidate.title.lowercase()
                if (candidateTitleLower.length >= 6 && targetTextCombined.contains(candidateTitleLower)) {
                    suggestedType = "REFERENCES"
                } else if (candidate.folderId == target.folderId && candidate.title.contains(target.title, ignoreCase = true) && candidate.id != target.id) {
                    suggestedType = "PART_OF"
                }
            }

            if (score >= minScoreThreshold) {
                val explanation = if (reasons.isEmpty()) "Related content" else reasons.joinToString(" • ")
                val confidence = (score.toFloat() / 100f).coerceIn(0.1f, 1.0f)
                results.add(
                    RelationshipCandidate(
                        item = candidate,
                        score = score,
                        suggestedType = suggestedType,
                        explanation = explanation,
                        confidence = confidence,
                        reasons = reasons
                    )
                )
            }
        }

        return results.sortedByDescending { it.score }.take(limit)
    }

    /**
     * Resolves directional relationship display text based on whether the current item is source or target.
     */
    fun getDisplayRelationType(relation: ItemRelationEntity, currentItemId: String): Pair<String, String> {
        val isSource = relation.sourceItemId == currentItemId
        return when (relation.relationType) {
            "REFERENCES" -> if (isSource) "REFERENCES" to "References" else "REFERENCED_BY" to "Referenced By"
            "PART_OF" -> if (isSource) "PART_OF" to "Part Of" else "HAS_PART" to "Includes / Has Part"
            "INSPIRED_BY" -> if (isSource) "INSPIRED_BY" to "Inspired By" else "INSPIRED" to "Inspired"
            "DERIVED_FROM" -> if (isSource) "DERIVED_FROM" to "Derived From" else "ORIGIN_OF" to "Source Of"
            "SUPPORTS" -> if (isSource) "SUPPORTS" to "Supports" else "SUPPORTED_BY" to "Supported By"
            "CONTRADICTS" -> "CONTRADICTS" to "Contradicts"
            "DUPLICATE_OF" -> "DUPLICATE_OF" to "Duplicate Of"
            else -> "RELATED_TO" to "Related To"
        }
    }

    /**
     * Extracts Discovered Topics deterministically by clustering tags, high-frequency keywords, and projects.
     */
    fun detectTopics(
        items: List<SavedItemEntity>,
        tagsMap: Map<String, List<String>>,
        projects: List<FolderEntity>
    ): List<DiscoveredTopic> {
        val nonSecretItems = items.filter { !it.isSecret }
        if (nonSecretItems.isEmpty()) return emptyList()

        val projectNamesById = projects.associate { it.id to it.name }
        val topicClusters = mutableMapOf<String, MutableList<SavedItemEntity>>()

        // Cluster by tags first
        for (item in nonSecretItems) {
            val itemTags = tagsMap[item.id] ?: emptyList()
            for (t in itemTags) {
                val clean = t.trim().lowercase().replaceFirstChar { it.uppercase() }
                if (clean.isNotBlank()) {
                    topicClusters.getOrPut(clean) { mutableListOf() }.add(item)
                }
            }
        }

        // Also identify frequent meaningful title words if tag clusters are sparse
        val wordFreq = mutableMapOf<String, MutableList<SavedItemEntity>>()
        for (item in nonSecretItems) {
            val tokens = tokenize(item.title)
            for (token in tokens) {
                if (token.length >= 4 && token !in STOP_WORDS) {
                    val capitalized = token.replaceFirstChar { it.uppercase() }
                    wordFreq.getOrPut(capitalized) { mutableListOf() }.add(item)
                }
            }
        }

        for ((word, wordItems) in wordFreq) {
            if (wordItems.size >= 2 && !topicClusters.containsKey(word)) {
                topicClusters[word] = wordItems
            }
        }

        val monthFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())

        return topicClusters.entries
            .filter { it.value.size >= 1 }
            .map { (topicName, clusterItems) ->
                val distinctItems = clusterItems.distinctBy { it.id }
                val clusterTags = distinctItems.flatMap { tagsMap[it.id] ?: emptyList() }
                    .filter { !it.equals(topicName, ignoreCase = true) }
                    .groupingBy { it }
                    .eachCount()
                    .entries.sortedByDescending { it.value }
                    .take(4)
                    .map { "#${it.key}" }

                val clusterProjects = distinctItems.mapNotNull { projectNamesById[it.folderId] }
                    .distinct()
                    .take(3)

                // Real monthly evolution using item creation timestamps
                val monthlyGrowth = distinctItems.groupBy {
                    monthFormat.format(Date(it.createdAt))
                }.mapValues { it.value.size }

                DiscoveredTopic(
                    name = topicName,
                    itemCount = distinctItems.size,
                    commonTags = clusterTags,
                    relatedProjects = clusterProjects,
                    sampleItems = distinctItems.sortedByDescending { it.createdAt }.take(5),
                    growthHistory = monthlyGrowth
                )
            }
            .sortedByDescending { it.itemCount }
    }

    /**
     * Discovery Signal: Forgotten Knowledge
     * Items saved > thresholdDays ago (default 60 days) and not updated or marked as favorite.
     */
    fun detectForgottenKnowledge(
        items: List<SavedItemEntity>,
        thresholdDays: Int = 60
    ): List<SavedItemEntity> {
        val cutoffMs = System.currentTimeMillis() - (thresholdDays.toLong() * 24 * 60 * 60 * 1000L)
        return items.filter { item ->
            !item.isSecret &&
            !item.isFavorite &&
            item.createdAt <= cutoffMs &&
            item.updatedAt <= cutoffMs
        }.sortedBy { it.updatedAt }
    }

    /**
     * Discovery Signal: Unexplored Knowledge
     * Items saved in Inbox or with empty notes/unreviewed.
     */
    fun detectUnexploredKnowledge(items: List<SavedItemEntity>): List<SavedItemEntity> {
        return items.filter { item ->
            !item.isSecret &&
            item.notes.isBlank() &&
            (item.folderId == "inbox_default_id" || item.folderId.isBlank())
        }.sortedByDescending { it.createdAt }
    }

    /**
     * Discovery Signal: You Keep Collecting
     * Detects tags/topics that have appeared >= 2 times in the last 14 days.
     */
    fun detectTrendingTopics(
        items: List<SavedItemEntity>,
        tagsMap: Map<String, List<String>>,
        days: Int = 14
    ): List<String> {
        val cutoffMs = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000L)
        val recentItems = items.filter { !it.isSecret && it.createdAt >= cutoffMs }
        val tagCounts = mutableMapOf<String, Int>()

        for (item in recentItems) {
            val tags = tagsMap[item.id] ?: emptyList()
            for (t in tags) {
                tagCounts[t] = tagCounts.getOrDefault(t, 0) + 1
            }
        }

        return tagCounts.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }

    /**
     * Builds item-centered graph data for interactive visual connection view.
     */
    fun buildItemGraph(
        currentItem: SavedItemEntity,
        relations: List<ItemRelationEntity>,
        allItemsMap: Map<String, SavedItemEntity>,
        projectName: String?,
        tags: List<String>
    ): ItemGraphData {
        val center = GraphNode(
            id = currentItem.id,
            label = currentItem.title,
            type = "CURRENT",
            isCenter = true,
            subtitle = currentItem.type
        )

        val connectedNodes = mutableListOf<GraphNode>()
        val edges = mutableListOf<GraphEdge>()

        // 1. Connected knowledge items
        for (rel in relations) {
            val otherId = if (rel.sourceItemId == currentItem.id) rel.targetItemId else rel.sourceItemId
            val otherItem = allItemsMap[otherId]
            if (otherItem != null && !otherItem.isSecret) {
                val (_, displayLabel) = getDisplayRelationType(rel, currentItem.id)
                connectedNodes.add(
                    GraphNode(
                        id = otherItem.id,
                        label = otherItem.title,
                        type = "RELATED",
                        subtitle = displayLabel
                    )
                )
                edges.add(
                    GraphEdge(
                        sourceId = currentItem.id,
                        targetId = otherItem.id,
                        label = displayLabel
                    )
                )
            }
        }

        // 2. Project node if present
        if (!projectName.isNullOrBlank() && currentItem.folderId != "inbox_default_id") {
            val projNodeId = "proj_${currentItem.folderId}"
            connectedNodes.add(
                GraphNode(
                    id = projNodeId,
                    label = projectName,
                    type = "PROJECT",
                    subtitle = "Project"
                )
            )
            edges.add(
                GraphEdge(
                    sourceId = currentItem.id,
                    targetId = projNodeId,
                    label = "Part of Project"
                )
            )
        }

        // 3. Top tags (up to 3)
        for (tag in tags.take(3)) {
            val tagNodeId = "tag_$tag"
            connectedNodes.add(
                GraphNode(
                    id = tagNodeId,
                    label = "#$tag",
                    type = "TAG",
                    subtitle = "Tag"
                )
            )
            edges.add(
                GraphEdge(
                    sourceId = currentItem.id,
                    targetId = tagNodeId,
                    label = "Tagged"
                )
            )
        }

        return ItemGraphData(
            centerNode = center,
            connectedNodes = connectedNodes,
            edges = edges
        )
    }

    private fun calculateJaccard(tokensA: Set<String>, tokensB: Set<String>): Float {
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0f
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return if (union > 0) intersection.toFloat() / union.toFloat() else 0f
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .toSet()
    }
}
