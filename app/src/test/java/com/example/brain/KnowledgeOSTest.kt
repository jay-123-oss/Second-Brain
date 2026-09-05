package com.example.brain

import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.ItemRelationEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.util.GapType
import com.example.brain.util.KnowledgeOSEngine
import org.junit.Assert.*
import org.junit.Test

class KnowledgeOSTest {

    private fun createDummyItem(
        id: String,
        title: String,
        type: String = "LINK",
        url: String? = null,
        folderId: String = "inbox_default_id",
        note: String = "",
        isFavorite: Boolean = false,
        isSecret: Boolean = false,
        maturity: String = "RAW",
        openCount: Int = 0,
        reviewAt: Long? = null,
        reviewStatus: String = "NONE",
        createdAt: Long = System.currentTimeMillis()
    ): SavedItemEntity {
        return SavedItemEntity(
            id = id,
            type = type,
            title = title,
            url = url,
            folderId = folderId,
            note = note,
            isFavorite = isFavorite,
            isSecret = isSecret,
            maturity = maturity,
            openCount = openCount,
            reviewAt = reviewAt,
            reviewStatus = reviewStatus,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    @Test
    fun testKnowledgePulseCalculation() {
        val item1 = createDummyItem("1", "Kotlin Guide", url = "https://kotlinlang.org/docs/home.html", folderId = "proj_1")
        val item2 = createDummyItem("2", "Android Architecture", url = "https://developer.android.com/guide", folderId = "proj_1")
        val item3 = createDummyItem("3", "Unorganized Note", folderId = "inbox_default_id")
        val secretItem = createDummyItem("4", "Secret Key", folderId = "vault_folder", isSecret = true)

        val folder1 = FolderEntity(id = "proj_1", name = "Android Project", isSecret = false)
        val secretFolder = FolderEntity(id = "vault_folder", name = "Secrets", isSecret = true)

        val relation = ItemRelationEntity(sourceItemId = "1", targetItemId = "2", relationType = "REFERENCES")

        val pulse = KnowledgeOSEngine.computePulse(
            items = listOf(item1, item2, item3, secretItem),
            relations = listOf(relation),
            folders = listOf(folder1, secretFolder)
        )

        // Secret items and secret folders must be strictly excluded
        assertEquals(3, pulse.totalItems)
        assertEquals(1, pulse.activeProjectsCount)
        assertEquals(1, pulse.totalConnections)
        assertEquals(0, pulse.reviewsPendingCount)
        assertEquals(0, pulse.notesCount)
    }

    @Test
    fun testKnowledgeGapsDetection() {
        // Create 3 items in proj_1 with no notes to trigger PROJECT_WITHOUT_NOTES
        val projItems = (1..3).map { i ->
            createDummyItem("proj_$i", "Project item $i", folderId = "proj_1", note = "")
        }

        // Create 1 item from 40 days ago with 0 opens to trigger UNREVIEWED_LONG_STANDING
        val oldItem = createDummyItem(
            id = "old_1",
            title = "Old Knowledge Item",
            folderId = "inbox_default_id",
            createdAt = System.currentTimeMillis() - (40L * 24 * 3600 * 1000),
            openCount = 0
        )

        val allItems = projItems + listOf(oldItem)
        val folders = listOf(FolderEntity(id = "proj_1", name = "Active Project", isSecret = false))
        val relations = emptyList<ItemRelationEntity>()

        val gaps = KnowledgeOSEngine.detectKnowledgeGaps(allItems, folders, relations)

        assertTrue("Should detect project without notes gap", gaps.any { it.type == GapType.PROJECT_WITHOUT_NOTES })
        assertTrue("Should detect unreviewed long-standing gap", gaps.any { it.type == GapType.UNREVIEWED_LONG_STANDING })
    }

    @Test
    fun testDomainIntelligenceAnalysis() {
        val items = listOf(
            createDummyItem("1", "GitHub Repo 1", url = "https://github.com/torvalds/linux"),
            createDummyItem("2", "GitHub Repo 2", url = "https://github.com/google/guava"),
            createDummyItem("3", "GitHub Issue", url = "https://github.com/facebook/react/issues/1"),
            createDummyItem("4", "Kotlin Docs", url = "https://kotlinlang.org/docs/coroutines-overview.html"),
            createDummyItem("5", "Invalid URL Item", url = "not-a-valid-url")
        )

        val domains = KnowledgeOSEngine.analyzeDomains(items)

        assertEquals(2, domains.size) // github.com and kotlinlang.org (invalid url filtered)
        val topDomain = domains.first()
        assertEquals("github.com", topDomain.domain)
        assertEquals(3, topDomain.itemCount)
    }

    @Test
    fun testCompareItems() {
        val itemA = createDummyItem(
            id = "a",
            title = "Jetpack Compose Internals",
            type = "ARTICLE",
            url = "https://compose.academy/internals",
            folderId = "folder_android",
            note = "Detailed architectural explanation of composer, slot table, and recomposition passes.",
            maturity = "REFINED"
        )
        val itemB = createDummyItem(
            id = "b",
            title = "Jetpack Compose Optimization",
            type = "ARTICLE",
            url = "https://developer.android.com/jetpack/compose/performance",
            folderId = "folder_android",
            note = "Guidelines on skipping recompositions and using derivedStateOf.",
            maturity = "CORE"
        )

        val tagsA = listOf("android", "compose", "internals")
        val tagsB = listOf("android", "compose", "performance")

        val comparison = KnowledgeOSEngine.compareItems(
            itemA = itemA,
            itemB = itemB,
            tagsA = tagsA,
            tagsB = tagsB
        )

        assertEquals(itemA, comparison.itemA)
        assertEquals(itemB, comparison.itemB)
        assertTrue(comparison.sharedTags.contains("android"))
        assertTrue(comparison.sharedTags.contains("compose"))
        assertTrue(comparison.similarities.any { it.contains("Both are ARTICLE items") })
        assertTrue(comparison.similarities.any { it.contains("Both are stored in the same project") })
        assertTrue(comparison.differences.any { it.contains("Different sources") })
    }

    @Test
    fun testSearchCommandParsing() {
        val rawQuery = "project:android tag:compose type:link review:due favorite:true source:github.com recomposition"

        var projectFilter: String? = null
        var tagFilter: String? = null
        var typeFilter: String? = null
        var domainFilter: String? = null
        var reviewFilter: Boolean? = null
        var favoriteFilter: Boolean? = null

        val tokens = rawQuery.split("\\s+".toRegex())
        val cleanTerms = mutableListOf<String>()

        for (token in tokens) {
            when {
                token.startsWith("project:", ignoreCase = true) -> {
                    projectFilter = token.substringAfter("project:").trim()
                }
                token.startsWith("tag:", ignoreCase = true) -> {
                    tagFilter = token.substringAfter("tag:").trim()
                }
                token.startsWith("type:", ignoreCase = true) -> {
                    typeFilter = token.substringAfter("type:").trim().uppercase()
                }
                token.startsWith("source:", ignoreCase = true) -> {
                    domainFilter = token.substringAfter("source:").trim().lowercase()
                }
                token.startsWith("review:", ignoreCase = true) -> {
                    val value = token.substringAfter("review:").trim().lowercase()
                    if (value == "due") reviewFilter = true
                }
                token.startsWith("favorite:", ignoreCase = true) -> {
                    val value = token.substringAfter("favorite:").trim().lowercase()
                    favoriteFilter = value == "true" || value == "1"
                }
                else -> {
                    cleanTerms.add(token)
                }
            }
        }

        assertEquals("android", projectFilter)
        assertEquals("compose", tagFilter)
        assertEquals("LINK", typeFilter)
        assertEquals("github.com", domainFilter)
        assertEquals(true, reviewFilter)
        assertEquals(true, favoriteFilter)
        assertEquals("recomposition", cleanTerms.joinToString(" "))
    }

    @Test
    fun testReviewDateFormatting() {
        val now = System.currentTimeMillis()
        val overdueTime = now - (2 * 86400000L)
        val futureTime = now + (3 * 86400000L)

        val overdueStr = KnowledgeOSEngine.formatReviewDate(overdueTime)
        assertTrue("Overdue date should contain 'Overdue'", overdueStr.contains("Overdue"))

        val futureStr = KnowledgeOSEngine.formatReviewDate(futureTime)
        assertTrue("Future date should contain 'Due'", futureStr.contains("Due"))
    }

}
