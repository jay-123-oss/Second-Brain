package com.example.brain.util

import android.content.Context
import android.content.SharedPreferences
import com.example.brain.data.entity.SavedItemEntity
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Modes of answering in Ask Your Brain
 */
enum class AssistantMode(val displayName: String) {
    SEARCH("Search Answer"),
    SUMMARIZE("Summarize"),
    EXPLAIN("Explain"),
    COMPARE("Compare")
}

data class SourceReference(
    val itemId: String,
    val title: String,
    val type: String,
    val relevantSnippet: String
)

data class AssistantResponse(
    val answer: String,
    val sources: List<SourceReference>,
    val mode: AssistantMode,
    val isGrounded: Boolean
)

/**
 * Model runtime status inspection
 */
enum class AIModelStatus(val label: String) {
    NOT_INSTALLED("Not Installed"),
    AVAILABLE("Available"),
    LOADING("Loading"),
    READY("Ready"),
    UNSUPPORTED("Unsupported"),
    FAILED("Failed")
}

/**
 * Local AI Settings persistence backed by SharedPreferences
 */
class LocalAISettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("second_brain_local_ai_prefs", Context.MODE_PRIVATE)

    var isLocalAIEnabled: Boolean
        get() = prefs.getBoolean("local_ai_enabled", true)
        set(value) = prefs.edit().putBoolean("local_ai_enabled", value).apply()

    var isAutoSummarizeEnabled: Boolean
        get() = prefs.getBoolean("auto_summarize_enabled", false)
        set(value) = prefs.edit().putBoolean("auto_summarize_enabled", value).apply()

    var isSemanticSearchEnabled: Boolean
        get() = prefs.getBoolean("semantic_search_enabled", true)
        set(value) = prefs.edit().putBoolean("semantic_search_enabled", value).apply()

    var isBackgroundAIEnabled: Boolean
        get() = prefs.getBoolean("background_ai_enabled", false)
        set(value) = prefs.edit().putBoolean("background_ai_enabled", value).apply()

    fun reset() {
        prefs.edit().clear().apply()
    }
}

/**
 * Interface contract for local intelligence, smart tagging, and connection discovery.
 */
interface LocalKnowledgeEngine {
    fun extractSuggestedTags(title: String, content: String?): List<String>
    fun findRelatedItems(target: SavedItemEntity, candidates: List<SavedItemEntity>, limit: Int = 3): List<SavedItemEntity>
}

/**
 * Canonical Local AI and Intelligence Engine:
 * - Deterministic heuristic tokenization
 * - 64-dimensional sparse hashed vector embeddings with sublinear term-frequency
 * - Cosine similarity calculation
 * - Extractive graph text summarizer (salience, position bonus, title overlap)
 * - Grounded Knowledge Assistant ("Ask Your Brain") with mandatory no-hallucination fallback
 * - 100% on-device, private, offline-first.
 */
object DeterministicLocalAIEngine : LocalKnowledgeEngine {

    const val EMBEDDING_DIMENSIONS = 128
    const val MODEL_VERSION = "local-vector-v1"

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

    // Common concepts and technologies for entity extraction
    private val KNOWN_CONCEPTS = listOf(
        "Android", "Kotlin", "Java", "Jetpack Compose", "Coroutines", "StateFlow", "Room",
        "SQLite", "FTS5", "WorkManager", "Biometrics", "Encryption", "Security", "AES-256",
        "Keystore", "Architecture", "MVVM", "Clean Architecture", "Dependency Injection",
        "Hilt", "Retrofit", "Coil", "Material Design", "Material 3", "Accessibility", "Dark Mode",
        "AI", "Machine Learning", "Transformers", "LLM", "RAG", "Embeddings", "Vector Search",
        "Cosine Similarity", "TextRank", "NLP", "Python", "PyTorch", "TensorFlow", "LiteRT",
        "ONNX", "Docker", "Kubernetes", "Git", "GitHub", "API", "REST", "JSON", "Protobuf"
    )

    // -------------------------------------------------------------
    // Tokenization & Feature Extraction
    // -------------------------------------------------------------

    fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && !STOP_WORDS.contains(it) }
    }

    override fun extractSuggestedTags(title: String, content: String?): List<String> {
        val combined = "$title ${content ?: ""}"
        val tokens = tokenize(combined)
        val wordCounts = mutableMapOf<String, Int>()

        for (token in tokens) {
            if (token.length >= 3) {
                wordCounts[token] = wordCounts.getOrDefault(token, 0) + 1
            }
        }

        return wordCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }

    // -------------------------------------------------------------
    // Vector Embeddings & Cosine Similarity
    // -------------------------------------------------------------

    /**
     * Generates a fixed 64-dimensional normalized vector for given text.
     * Uses feature hashing with sublinear term-frequency weighting (1 + ln(tf)).
     */
    fun computeEmbedding(text: String): FloatArray {
        val vector = FloatArray(EMBEDDING_DIMENSIONS)
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return vector

        val termFrequencies = mutableMapOf<String, Int>()
        for (token in tokens) {
            termFrequencies[token] = termFrequencies.getOrDefault(token, 0) + 1
        }

        for ((term, count) in termFrequencies) {
            val hash = (term.hashCode() and 0x7FFFFFFF) % EMBEDDING_DIMENSIONS
            val weight = (1.0 + ln(count.toDouble())).toFloat()
            vector[hash] += weight
        }

        // L2 Normalize
        var sumSquares = 0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares.toDouble()).toFloat()
        if (norm > 0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }

    /**
     * Serializes FloatArray to pure Kotlin string representation "[0.123,0.456,...]"
     */
    fun serializeEmbedding(vector: FloatArray): String {
        return "[" + vector.joinToString(",") { "%.4f".format(java.util.Locale.US, it) } + "]"
    }

    /**
     * Deserializes pure Kotlin string representation to FloatArray
     */
    fun deserializeEmbedding(json: String?): FloatArray {
        if (json.isNullOrBlank()) return FloatArray(EMBEDDING_DIMENSIONS)
        val clean = json.trim().removeSurrounding("[", "]")
        if (clean.isBlank()) return FloatArray(EMBEDDING_DIMENSIONS)
        return try {
            val parts = clean.split(",")
            val array = FloatArray(parts.size)
            for (i in parts.indices) {
                array[i] = parts[i].trim().toFloatOrNull() ?: 0f
            }
            array
        } catch (_: Exception) {
            FloatArray(EMBEDDING_DIMENSIONS)
        }
    }

    /**
     * Computes cosine similarity between two normalized vectors in range [-1.0, 1.0].
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return 0f
        var dot = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        val denom = sqrt((norm1 * norm2).toDouble()).toFloat()
        return if (denom > 0f) (dot / denom).coerceIn(-1f, 1f) else 0f
    }

    override fun findRelatedItems(
        target: SavedItemEntity,
        candidates: List<SavedItemEntity>,
        limit: Int
    ): List<SavedItemEntity> {
        val targetText = "${target.title} ${target.notes} ${target.description ?: ""}"
        val targetVector = computeEmbedding(targetText)

        return candidates
            .filter { it.id != target.id && !it.isSecret }
            .map { candidate ->
                val candText = "${candidate.title} ${candidate.notes} ${candidate.description ?: ""}"
                val candVector = computeEmbedding(candText)
                val sim = cosineSimilarity(targetVector, candVector)
                Pair(candidate, sim)
            }
            .filter { it.second > 0.15f }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    // -------------------------------------------------------------
    // Extractive Graph Text Summarizer
    // -------------------------------------------------------------

    /**
     * Generates a 2-3 sentence grounded extractive summary of the item's content.
     */
    fun summarize(item: SavedItemEntity): String {
        val rawText = buildString {
            if (item.title.isNotBlank()) append(item.title).append(". ")
            if (!item.description.isNullOrBlank()) append(item.description).append(". ")
            if (item.notes.isNotBlank()) append(item.notes).append(". ")
            if (!item.extractedText.isNullOrBlank()) append(item.extractedText)
        }.trim()

        if (rawText.isBlank() || rawText.split(Regex("\\s+")).size < 12) {
            return "Content is too brief for an AI summary. Title: ${item.title.ifBlank { "Untitled" }}."
        }

        // Split into sentences
        val sentences = rawText.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length >= 15 && it.split(Regex("\\s+")).size >= 4 }

        if (sentences.isEmpty()) {
            return item.title
        }

        if (sentences.size <= 2) {
            return sentences.joinToString(" ")
        }

        val titleTokens = tokenize(item.title).toSet()

        // Score sentences by position, length, and title overlap
        val scoredSentences = sentences.mapIndexed { index, sentence ->
            val sTokens = tokenize(sentence).toSet()
            var score = 0f

            // Position bonus (lead sentences contain main idea)
            if (index == 0) score += 0.45f
            if (index == 1) score += 0.25f

            // Title overlap bonus
            val overlap = titleTokens.intersect(sTokens).size
            if (titleTokens.isNotEmpty()) {
                score += (overlap.toFloat() / titleTokens.size.toFloat()) * 0.40f
            }

            // Word richness
            if (sTokens.size in 6..25) {
                score += 0.20f
            }

            Triple(index, sentence, score)
        }

        // Pick top 2 or 3 sentences
        val targetCount = if (sentences.size >= 6) 3 else 2
        val topPicks = scoredSentences
            .sortedByDescending { it.third }
            .take(targetCount)
            .sortedBy { it.first } // Re-sort by original flow

        return topPicks.joinToString(" ") { it.second }
    }

    // -------------------------------------------------------------
    // Entity & Concept Extraction
    // -------------------------------------------------------------

    fun extractConcepts(item: SavedItemEntity): List<String> {
        val text = "${item.title} ${item.description ?: ""} ${item.notes} ${item.extractedText ?: ""}"
        val found = mutableListOf<String>()

        for (concept in KNOWN_CONCEPTS) {
            val pattern = Regex("\\b${Regex.escape(concept)}\\b", RegexOption.IGNORE_CASE)
            if (pattern.containsMatchIn(text)) {
                found.add(concept)
            }
        }

        return found.distinct().take(6)
    }

    // -------------------------------------------------------------
    // "Ask Your Brain" Knowledge Assistant (Grounded Q&A)
    // -------------------------------------------------------------

    /**
     * Answers user question grounded strictly in their saved items.
     * Vault items are strictly excluded.
     * If information is missing, mandatory fallback returns:
     * "I couldn't find enough information in your saved knowledge to answer that confidently."
     */
    fun answerQuestion(
        question: String,
        activeItems: List<SavedItemEntity>,
        mode: AssistantMode = AssistantMode.SEARCH
    ): AssistantResponse {
        val cleanQ = question.trim()
        if (cleanQ.isBlank()) {
            return AssistantResponse(
                answer = "Please enter a question about your saved knowledge.",
                sources = emptyList(),
                mode = mode,
                isGrounded = false
            )
        }

        // Strictly exclude secret/Vault items
        val candidateItems = activeItems.filter { !it.isSecret }
        if (candidateItems.isEmpty()) {
            return AssistantResponse(
                answer = "I couldn't find enough information in your saved knowledge to answer that confidently.",
                sources = emptyList(),
                mode = mode,
                isGrounded = false
            )
        }

        val qTokens = tokenize(cleanQ).toSet()
        val qVector = computeEmbedding(cleanQ)

        // Rank candidates via hybrid score: Lexical Jaccard + Cosine Similarity
        val ranked = candidateItems.mapNotNull { item ->
            val itemText = "${item.title} ${item.description ?: ""} ${item.notes} ${item.extractedText ?: ""}"
            val iTokens = tokenize(itemText).toSet()
            val iVector = computeEmbedding(itemText)

            val lexicalOverlap = qTokens.intersect(iTokens).size
            val lexicalScore = if (qTokens.isNotEmpty()) lexicalOverlap.toFloat() / qTokens.size.toFloat() else 0f
            val semanticScore = cosineSimilarity(qVector, iVector)

            // Guard against coincidence: if query has 3 or more words, 1 incidental token match with low semantic similarity is discarded
            if (qTokens.size >= 3 && lexicalOverlap < 2 && semanticScore < 0.30f) {
                return@mapNotNull null
            }

            // Hybrid score
            val combinedScore = 0.50f * lexicalScore + 0.50f * (if (semanticScore > 0) semanticScore else 0f)

            if (combinedScore < 0.20f) {
                return@mapNotNull null
            }

            // Extract best snippet
            val sentences = itemText.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
            val bestSentence = sentences.maxByOrNull { s ->
                val sTokens = tokenize(s).toSet()
                qTokens.intersect(sTokens).size
            } ?: item.description ?: item.title

            Triple(item, combinedScore, bestSentence)
        }
            .sortedByDescending { it.second }

        // Mandatory No-Hallucination Fallback:
        if (ranked.isEmpty() || ranked.first().second < 0.22f) {
            return AssistantResponse(
                answer = "I couldn't find enough information in your saved knowledge to answer that confidently.",
                sources = emptyList(),
                mode = mode,
                isGrounded = false
            )
        }

        val topMatches = ranked.take(4)
        val sources = topMatches.map { (item, _, snippet) ->
            SourceReference(
                itemId = item.id,
                title = item.title.ifBlank { "Untitled" },
                type = item.type,
                relevantSnippet = snippet.take(160)
            )
        }

        // Synthesize answer based on mode
        val synthesizedAnswer = when (mode) {
            AssistantMode.SUMMARIZE -> {
                val summaries = topMatches.mapIndexed { idx, (item, _, _) ->
                    val sum = summarize(item)
                    "• **${item.title}**: $sum"
                }.joinToString("\n\n")
                "Here is a grounded summary from your saved knowledge:\n\n$summaries"
            }

            AssistantMode.EXPLAIN -> {
                val keyPoints = topMatches.map { (item, _, snippet) ->
                    "From \"${item.title}\": ${snippet.trim()}"
                }.joinToString("\n\n")
                "Based on your saved notes and materials:\n\n$keyPoints"
            }

            AssistantMode.COMPARE -> {
                if (topMatches.size < 2) {
                    val single = topMatches.first()
                    "Only one relevant item was found to compare: \"${single.first.title}\" (${single.third}). Save additional notes to compare differences."
                } else {
                    val comp = topMatches.take(2).mapIndexed { idx, (item, _, snippet) ->
                        "**Source ${idx + 1}: ${item.title}**\n${snippet.trim()}"
                    }.joinToString("\n\n")
                    "Comparison across your saved sources:\n\n$comp"
                }
            }

            AssistantMode.SEARCH -> {
                val primary = topMatches.first()
                val others = topMatches.drop(1)

                val answerBody = buildString {
                    append("Based on **${primary.first.title}**, ")
                    append(primary.third.trim())
                    if (!primary.third.trim().endsWith(".")) append(".")

                    if (others.isNotEmpty()) {
                        append("\n\nAdditionally, your notes on **${others.first().first.title}** mention: ")
                        append(others.first().third.trim())
                        if (!others.first().third.trim().endsWith(".")) append(".")
                    }
                }
                answerBody
            }
        }

        return AssistantResponse(
            answer = synthesizedAnswer,
            sources = sources,
            mode = mode,
            isGrounded = true
        )
    }
}
