package com.example.brain.util

import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.ItemRelationEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.data.entity.TagEntity
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight metric snapshot for Knowledge Pulse.
 * 100% computed from real local data without artificial gamification, streaks, or XP.
 */
data class KnowledgePulse(
    val totalItems: Int,
    val capturedToday: Int,
    val capturedThisWeek: Int,
    val totalConnections: Int,
    val activeProjectsCount: Int,
    val reviewsPendingCount: Int,
    val notesCount: Int
)

/**
 * An explainable knowledge gap surfaced by the system.
 */
data class KnowledgeGap(
    val id: String,
    val title: String,
    val description: String,
    val type: GapType,
    val relatedEntityId: String?,
    val actionLabel: String
)

enum class GapType {
    PROJECT_WITHOUT_NOTES,
    TOPIC_WITHOUT_CONNECTIONS,
    PROJECT_WITHOUT_SUMMARY,
    UNREVIEWED_LONG_STANDING
}

/**
 * Domain & Source Intelligence representation
 */
data class DomainSourceInfo(
    val domain: String,
    val displayName: String,
    val category: String, // Code, Video, Reading, Docs, General
    val itemCount: Int
)

/**
 * Structured comparison result between two knowledge items.
 */
data class ItemComparison(
    val itemA: SavedItemEntity,
    val itemB: SavedItemEntity,
    val sharedTags: List<String>,
    val sharedConcepts: List<String>,
    val similarities: List<String>,
    val differences: List<String>
)

/**
 * Core Knowledge OS Engine:
 * - Computes real Knowledge Pulse
 * - Detects explainable Knowledge Gaps
 * - Analyzes Source & Domain intelligence
 * - Explains relationships ("Why is this related?")
 * - Generates structured knowledge comparisons
 * - Local preference learning for one-tap capture organization
 */
object KnowledgeOSEngine {

    // -------------------------------------------------------------
    // Knowledge Pulse
    // -------------------------------------------------------------

    fun computePulse(
        items: List<SavedItemEntity>,
        relations: List<ItemRelationEntity>,
        folders: List<FolderEntity>
    ): KnowledgePulse {
        val nonVaultItems = items.filter { !it.isSecret }
        val now = System.currentTimeMillis()
        val oneDayAgo = now - (24 * 3600 * 1000L)
        val oneWeekAgo = now - (7 * 24 * 3600 * 1000L)

        val capturedToday = nonVaultItems.count { it.createdAt >= oneDayAgo }
        val capturedThisWeek = nonVaultItems.count { it.createdAt >= oneWeekAgo }
        val reviewsPending = nonVaultItems.count {
            it.reviewAt != null && it.reviewAt <= now && it.reviewStatus != "REVIEWED"
        }
        val notesCount = nonVaultItems.count { it.notes.isNotBlank() }

        return KnowledgePulse(
            totalItems = nonVaultItems.size,
            capturedToday = capturedToday,
            capturedThisWeek = capturedThisWeek,
            totalConnections = relations.size,
            activeProjectsCount = folders.filter { !it.isSecret && it.id != "inbox_default_id" }.size,
            reviewsPendingCount = reviewsPending,
            notesCount = notesCount
        )
    }

    // -------------------------------------------------------------
    // Knowledge Gaps
    // -------------------------------------------------------------

    fun detectKnowledgeGaps(
        items: List<SavedItemEntity>,
        folders: List<FolderEntity>,
        relations: List<ItemRelationEntity>
    ): List<KnowledgeGap> {
        val nonVaultItems = items.filter { !it.isSecret }
        val gaps = mutableListOf<KnowledgeGap>()

        // 1. Projects with >= 3 items but 0 personal notes
        val itemsByFolder = nonVaultItems.groupBy { it.folderId }
        for (folder in folders.filter { !it.isSecret && it.id != "inbox_default_id" }) {
            val folderItems = itemsByFolder[folder.id] ?: emptyList()
            if (folderItems.size >= 3) {
                val hasNotes = folderItems.any { it.notes.isNotBlank() }
                if (!hasNotes) {
                    gaps.add(
                        KnowledgeGap(
                            id = "gap_notes_${folder.id}",
                            title = "Missing Personal Notes",
                            description = "You have ${folderItems.size} resources in \"${folder.name}\" but haven't written any personal notes or takeaways yet.",
                            type = GapType.PROJECT_WITHOUT_NOTES,
                            relatedEntityId = folder.id,
                            actionLabel = "Add Note"
                        )
                    )
                }
            }
        }

        // 2. High-volume projects without any connections between items
        val connectionItemIds = relations.flatMap { listOf(it.sourceItemId, it.targetItemId) }.toSet()
        for (folder in folders.filter { !it.isSecret && it.id != "inbox_default_id" }) {
            val folderItems = itemsByFolder[folder.id] ?: emptyList()
            if (folderItems.size >= 4) {
                val connectedInFolder = folderItems.count { connectionItemIds.contains(it.id) }
                if (connectedInFolder == 0) {
                    gaps.add(
                        KnowledgeGap(
                            id = "gap_conn_${folder.id}",
                            title = "Unconnected Resources",
                            description = "\"${folder.name}\" has ${folderItems.size} items that are isolated from each other with no relationships mapped.",
                            type = GapType.TOPIC_WITHOUT_CONNECTIONS,
                            relatedEntityId = folder.id,
                            actionLabel = "Connect Items"
                        )
                    )
                }
            }
        }

        // 3. Dense projects without local summary
        for (folder in folders.filter { !it.isSecret && it.id != "inbox_default_id" }) {
            val folderItems = itemsByFolder[folder.id] ?: emptyList()
            if (folderItems.size >= 5) {
                val summarizedCount = folderItems.count { !it.summary.isNullOrBlank() }
                if (summarizedCount == 0) {
                    gaps.add(
                        KnowledgeGap(
                            id = "gap_sum_${folder.id}",
                            title = "Unsummarized Project",
                            description = "\"${folder.name}\" has grown to ${folderItems.size} resources. Generate local summaries to capture the core essence.",
                            type = GapType.PROJECT_WITHOUT_SUMMARY,
                            relatedEntityId = folder.id,
                            actionLabel = "Review Knowledge"
                        )
                    )
                }
            }
        }

        // 4. Important items unreviewed for > 30 days
        val thresholdLongStanding = System.currentTimeMillis() - (30L * 24 * 3600 * 1000)
        val longStandingItems = nonVaultItems.filter {
            it.createdAt <= thresholdLongStanding && it.openCount == 0 && it.reviewStatus != "REVIEWED"
        }
        if (longStandingItems.isNotEmpty()) {
            gaps.add(
                KnowledgeGap(
                    id = "gap_unreviewed_long_standing",
                    title = "Forgotten Knowledge",
                    description = "You have ${longStandingItems.size} items captured over a month ago that haven't been reviewed or opened yet.",
                    type = GapType.UNREVIEWED_LONG_STANDING,
                    relatedEntityId = longStandingItems.first().id,
                    actionLabel = "Review Now"
                )
            )
        }

        return gaps.take(3) // Keep top explainable gaps, do not overwhelm
    }

    // -------------------------------------------------------------
    // Domain & Source Intelligence
    // -------------------------------------------------------------

    fun extractDomain(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        return try {
            val uri = URI(if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) "https://$rawUrl" else rawUrl)
            val host = uri.host ?: return null
            if (!host.contains('.')) return null
            host.removePrefix("www.").lowercase()
        } catch (_: Exception) {
            null
        }
    }


    fun categorizeDomain(domain: String): String {
        return when {
            domain.contains("github.com") || domain.contains("gitlab.com") || domain.contains("stackoverflow.com") -> "Code & Development"
            domain.contains("youtube.com") || domain.contains("youtu.be") || domain.contains("vimeo.com") -> "Video & Audio"
            domain.contains("medium.com") || domain.contains("substack.com") || domain.contains("dev.to") -> "Articles & Blogs"
            domain.contains("android.com") || domain.contains("developer.") || domain.contains("docs.") -> "Documentation"
            domain.contains("arxiv.org") || domain.contains("nature.com") || domain.contains("acm.org") || domain.contains("ieee.org") -> "Research & Academic"
            domain.contains("news") || domain.contains("nytimes") || domain.contains("bbc") || domain.contains("reuters") -> "News & Media"
            else -> "Web Resources"
        }
    }

    fun analyzeDomains(items: List<SavedItemEntity>): List<DomainSourceInfo> {
        val nonVaultItems = items.filter { !it.isSecret }
        val domainCounts = mutableMapOf<String, Int>()

        for (item in nonVaultItems) {
            val domain = extractDomain(item.url) ?: extractDomain(item.originalUrl)
            if (domain != null) {
                domainCounts[domain] = domainCounts.getOrDefault(domain, 0) + 1
            }
        }

        return domainCounts.entries
            .sortedByDescending { it.value }
            .take(8)
            .map { (domain, count) ->
                DomainSourceInfo(
                    domain = domain,
                    displayName = domain.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() },
                    category = categorizeDomain(domain),
                    itemCount = count
                )
            }
    }

    // -------------------------------------------------------------
    // Explainability Engine ("Why is this related?")
    // -------------------------------------------------------------

    fun explainRelationship(
        itemA: SavedItemEntity,
        itemB: SavedItemEntity,
        tagsA: List<String> = emptyList(),
        tagsB: List<String> = emptyList(),
        folders: List<FolderEntity> = emptyList()
    ): String {
        // 1. Same project
        if (itemA.folderId == itemB.folderId && itemA.folderId != "inbox_default_id") {
            val folderName = folders.find { it.id == itemA.folderId }?.name ?: "same project"
            return "Both belong to $folderName"
        }

        // 2. Shared tags
        val sharedTags = tagsA.intersect(tagsB.toSet())
        if (sharedTags.isNotEmpty()) {
            val sample = sharedTags.take(2).joinToString(", ") { "#$it" }
            return "Shares ${sharedTags.size} tag${if (sharedTags.size > 1) "s" else ""} ($sample)"
        }

        // 3. Same source domain
        val domainA = extractDomain(itemA.url)
        val domainB = extractDomain(itemB.url)
        if (domainA != null && domainB != null && domainA == domainB) {
            return "From the same source ($domainA)"
        }

        // 4. Shared title keywords
        val tokensA = DeterministicLocalAIEngine.tokenize(itemA.title).toSet()
        val tokensB = DeterministicLocalAIEngine.tokenize(itemB.title).toSet()
        val sharedTokens = tokensA.intersect(tokensB)
        if (sharedTokens.size >= 2) {
            return "Covers similar concepts: ${sharedTokens.take(2).joinToString(", ")}"
        }

        // 5. Semantic similarity
        return "Connected knowledge context"
    }

    // -------------------------------------------------------------
    // Knowledge Comparison Engine
    // -------------------------------------------------------------

    fun compareItems(
        itemA: SavedItemEntity,
        itemB: SavedItemEntity,
        tagsA: List<String>,
        tagsB: List<String>
    ): ItemComparison {
        val sharedTags = tagsA.intersect(tagsB.toSet()).toList()
        val conceptsA = DeterministicLocalAIEngine.extractConcepts(itemA)
        val conceptsB = DeterministicLocalAIEngine.extractConcepts(itemB)
        val sharedConcepts = conceptsA.intersect(conceptsB.toSet()).toList()

        val similarities = mutableListOf<String>()
        val differences = mutableListOf<String>()

        if (itemA.type == itemB.type) {
            similarities.add("Both are ${itemA.type} items")
        } else {
            differences.add("Type: ${itemA.type} vs ${itemB.type}")
        }

        if (itemA.folderId == itemB.folderId) {
            similarities.add("Both are stored in the same project")
        } else {
            differences.add("Stored in different projects")
        }

        val domainA = extractDomain(itemA.url)
        val domainB = extractDomain(itemB.url)
        if (domainA != null && domainB != null) {
            if (domainA == domainB) {
                similarities.add("Same source: $domainA")
            } else {
                differences.add("Different sources: $domainA vs $domainB")
            }
        }

        val wordsA = (itemA.title + " " + itemA.notes + " " + (itemA.extractedText ?: "")).split(Regex("\\s+")).size
        val wordsB = (itemB.title + " " + itemB.notes + " " + (itemB.extractedText ?: "")).split(Regex("\\s+")).size
        differences.add("Content depth: ~$wordsA words vs ~$wordsB words")

        if (itemA.notes.isNotBlank() && itemB.notes.isNotBlank()) {
            similarities.add("Both contain personal observations")
        } else if (itemA.notes.isNotBlank()) {
            differences.add("\"${itemA.title}\" has personal notes; \"${itemB.title}\" does not")
        } else if (itemB.notes.isNotBlank()) {
            differences.add("\"${itemB.title}\" has personal notes; \"${itemA.title}\" does not")
        }

        return ItemComparison(
            itemA = itemA,
            itemB = itemB,
            sharedTags = sharedTags,
            sharedConcepts = sharedConcepts,
            similarities = similarities,
            differences = differences
        )
    }

    // -------------------------------------------------------------
    // Formatters & Helper Signals
    // -------------------------------------------------------------

    fun formatReviewDate(timestamp: Long?): String {
        if (timestamp == null) return "No review date"
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val dateStr = sdf.format(Date(timestamp))
        return when {
            timestamp < now -> "Overdue ($dateStr)"
            else -> "Due $dateStr"
        }
    }


    fun isReviewDue(reviewAt: Long?): Boolean {
        if (reviewAt == null) return false
        return reviewAt <= System.currentTimeMillis()
    }
}
