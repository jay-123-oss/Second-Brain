package com.example.brain

import com.example.brain.data.entity.FolderEntity
import com.example.brain.data.entity.ItemRelationEntity
import com.example.brain.data.entity.SavedItemEntity
import com.example.brain.util.KnowledgeIntelligenceEngine
import com.example.brain.util.SmartCollectionRule
import org.junit.Assert.*
import org.junit.Test

class KnowledgeIntelligenceTest {

    private fun createDummyItem(
        id: String,
        title: String,
        type: String = "LINK",
        url: String? = null,
        folderId: String = "folder_1",
        notes: String = "",
        isFavorite: Boolean = false,
        isSecret: Boolean = false,
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    ): SavedItemEntity {
        return SavedItemEntity(
            id = id,
            type = type,
            title = title,
            url = url,
            folderId = folderId,
            note = notes,
            isFavorite = isFavorite,
            isSecret = isSecret,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    @Test
    fun testDuplicateDetectionVsRelatedness() {
        val source = createDummyItem(
            id = "item_1",
            title = "Attention Is All You Need",
            url = "https://arxiv.org/abs/1706.03762?utm_source=twitter"
        )
        val duplicateCandidate = createDummyItem(
            id = "item_2",
            title = "Attention Is All You Need Mirror",
            url = "https://arxiv.org/abs/1706.03762?ref=brain"
        )
        val relatedCandidate = createDummyItem(
            id = "item_3",
            title = "Transformer Architecture Deep Dive",
            url = "https://arxiv.org/abs/2005.14165",
            folderId = "folder_1"
        )

        val candidates = KnowledgeIntelligenceEngine.findRelatedCandidates(
            target = source,
            candidates = listOf(duplicateCandidate, relatedCandidate),
            targetTags = listOf("ai", "transformers"),
            candidatesTagsMap = mapOf("item_3" to listOf("ai", "deep-learning"))
        )

        // Duplicate item should have score >= 95 and type DUPLICATE_OF
        val duplicateMatch = candidates.find { it.item.id == "item_2" }
        assertNotNull("Duplicate must be found", duplicateMatch)
        assertEquals("DUPLICATE_OF", duplicateMatch?.suggestedType)
        assertTrue(duplicateMatch!!.score >= 90)
        assertTrue(duplicateMatch.explanation.contains("Identical normalized web address"))

        // Related item should have type RELATED_TO and transparent reasons
        val relatedMatch = candidates.find { it.item.id == "item_3" }
        assertNotNull("Related item must be found", relatedMatch)
        assertTrue(relatedMatch!!.score >= 30)
        assertTrue(relatedMatch.reasons.any { it.contains("Same domain") || it.contains("Shared tags") || it.contains("Same project") })
    }

    @Test
    fun testDirectionalRelationshipSemantics() {
        val rel = ItemRelationEntity(
            sourceItemId = "item_A",
            targetItemId = "item_B",
            relationType = "REFERENCES"
        )

        // Item A references Item B
        val (typeFromA, labelFromA) = KnowledgeIntelligenceEngine.getDisplayRelationType(rel, "item_A")
        assertEquals("REFERENCES", typeFromA)
        assertEquals("References", labelFromA)

        // From perspective of Item B, it is Referenced By Item A
        val (typeFromB, labelFromB) = KnowledgeIntelligenceEngine.getDisplayRelationType(rel, "item_B")
        assertEquals("REFERENCED_BY", typeFromB)
        assertEquals("Referenced By", labelFromB)

        // Test Part Of direction
        val partRel = ItemRelationEntity(
            sourceItemId = "module_1",
            targetItemId = "system_root",
            relationType = "PART_OF"
        )
        val (_, sourcePartLabel) = KnowledgeIntelligenceEngine.getDisplayRelationType(partRel, "module_1")
        val (_, targetPartLabel) = KnowledgeIntelligenceEngine.getDisplayRelationType(partRel, "system_root")
        assertEquals("Part Of", sourcePartLabel)
        assertEquals("Includes / Has Part", targetPartLabel)
    }

    @Test
    fun testVaultIsolation() {
        val normalItem = createDummyItem(id = "norm_1", title = "Public ML Paper", isSecret = false)
        val vaultItem = createDummyItem(id = "vault_1", title = "Private Bank Keys", isSecret = true)

        // 1. Vault item excluded from candidates
        val candidates = KnowledgeIntelligenceEngine.findRelatedCandidates(
            target = normalItem,
            candidates = listOf(vaultItem)
        )
        assertTrue("Vault item must never appear in candidates", candidates.isEmpty())

        // 2. Normal item querying with vault target yields nothing
        val candidatesFromVault = KnowledgeIntelligenceEngine.findRelatedCandidates(
            target = vaultItem,
            candidates = listOf(normalItem)
        )
        assertTrue("Vault item target must return empty candidates", candidatesFromVault.isEmpty())

        // 3. Topics must exclude secret items
        val topics = KnowledgeIntelligenceEngine.detectTopics(
            items = listOf(normalItem, vaultItem),
            tagsMap = mapOf("norm_1" to listOf("ai"), "vault_1" to listOf("secret_tag")),
            projects = emptyList()
        )
        assertFalse("Topics must not include vault tags", topics.any { it.name.contains("secret", ignoreCase = true) })
    }

    @Test
    fun testForgottenAndUnexploredKnowledge() {
        val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000L)
        val forgottenItem = createDummyItem(
            id = "old_1",
            title = "Forgotten Research 2025",
            createdAt = ninetyDaysAgo,
            updatedAt = ninetyDaysAgo,
            isFavorite = false
        )
        val recentItem = createDummyItem(
            id = "new_1",
            title = "Fresh Note",
            createdAt = System.currentTimeMillis()
        )
        val favoriteOldItem = createDummyItem(
            id = "fav_old",
            title = "Old Favorite",
            createdAt = ninetyDaysAgo,
            updatedAt = ninetyDaysAgo,
            isFavorite = true
        )

        val forgottenList = KnowledgeIntelligenceEngine.detectForgottenKnowledge(
            listOf(forgottenItem, recentItem, favoriteOldItem),
            thresholdDays = 60
        )

        assertEquals(1, forgottenList.size)
        assertEquals("old_1", forgottenList[0].id)

        // Test Unexplored Knowledge (inbox and empty notes)
        val unexploredItem = createDummyItem(
            id = "unexplored_1",
            title = "Unexplored Item",
            folderId = "inbox_default_id",
            notes = ""
        )
        val exploredItem = createDummyItem(
            id = "explored_1",
            title = "Explored Item",
            notes = "Contains detailed notes"
        )

        val unexploredList = KnowledgeIntelligenceEngine.detectUnexploredKnowledge(
            listOf(unexploredItem, exploredItem)
        )
        assertEquals(1, unexploredList.size)
        assertEquals("unexplored_1", unexploredList[0].id)
    }

    @Test
    fun testSmartCollectionRuleEvaluation() {
        val pdfItem = createDummyItem(id = "item_pdf", title = "Deep Learning Book", type = "PDF", isFavorite = true)
        val linkItem = createDummyItem(id = "item_link", title = "Deep Learning Article", type = "LINK", isFavorite = false)

        val rule = SmartCollectionRule(
            contentType = "PDF",
            isFavorite = true
        )

        assertTrue(rule.matches(pdfItem, listOf("ai")))
        assertFalse(rule.matches(linkItem, listOf("ai")))

        // Rule with tag
        val tagRule = SmartCollectionRule(tag = "research")
        assertTrue(tagRule.matches(pdfItem, listOf("ai", "research")))
        assertFalse(tagRule.matches(pdfItem, listOf("ai", "general")))

        // Serialization check
        val json = rule.toJson()
        val parsed = SmartCollectionRule.fromJson(json)
        assertEquals("PDF", parsed.contentType)
        assertEquals(true, parsed.isFavorite)
    }

    @Test
    fun testTopicDetectionAndEvolution() {
        val item1 = createDummyItem(id = "1", title = "Transformer Models in NLP")
        val item2 = createDummyItem(id = "2", title = "Transformer Attention Mechanics")
        val projects = listOf(FolderEntity(id = "folder_1", name = "AI Research", isSecret = false))

        val topics = KnowledgeIntelligenceEngine.detectTopics(
            items = listOf(item1, item2),
            tagsMap = mapOf("1" to listOf("ai", "transformers"), "2" to listOf("ai", "deep-learning")),
            projects = projects
        )

        assertTrue(topics.isNotEmpty())
        val aiTopic = topics.find { it.name.equals("Ai", ignoreCase = true) }
        assertNotNull("Ai topic should be detected", aiTopic)
        assertEquals(2, aiTopic?.itemCount)
        assertTrue(aiTopic?.growthHistory?.isNotEmpty() == true)
    }
}
