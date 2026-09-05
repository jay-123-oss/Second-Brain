package com.example.brain.util

import android.content.Intent
import android.net.Uri
import com.example.brain.data.model.CapturedContent
import com.example.brain.data.model.ContentType
import java.util.regex.Pattern

/**
 * Intelligent, deterministic parser for incoming Android Share Sheet intents.
 * Supports ACTION_SEND and ACTION_SEND_MULTIPLE across URLs, rich text, images, videos, audio, and documents.
 */
object ShareIntentParser {

    private val URL_REGEX = Pattern.compile(
        "https?://(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)"
    )

    fun parse(intent: Intent?): CapturedContent? {
        if (intent == null) return null
        val action = intent.action ?: return null

        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return null
        }

        val mimeType = intent.type ?: ""
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""

        // 1. Handle Multiple Shared Streams (ACTION_SEND_MULTIPLE)
        if (action == Intent.ACTION_SEND_MULTIPLE) {
            val uriList = ArrayList<Uri>()
            @Suppress("DEPRECATION")
            val streamList = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (!streamList.isNullOrEmpty()) {
                uriList.addAll(streamList.filterNotNull())
            } else if (intent.clipData != null) {
                val clip = intent.clipData!!
                for (i in 0 until clip.itemCount) {
                    val itemUri = clip.getItemAt(i).uri
                    if (itemUri != null) uriList.add(itemUri)
                }
            }

            if (uriList.isNotEmpty()) {
                val firstUri = uriList.first()
                val detectedType = classifyMime(mimeType, firstUri.toString())
                return CapturedContent(
                    type = detectedType,
                    title = subject.ifBlank { "Shared ${uriList.size} items" },
                    uri = firstUri,
                    uris = uriList
                )
            }
        }

        // 2. Handle Single Streams (ACTION_SEND with EXTRA_STREAM or ClipData URI)
        @Suppress("DEPRECATION")
        val streamUri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

        val accompanyingText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

        if (streamUri != null) {
            val detectedType = classifyMime(mimeType, streamUri.toString())
            return CapturedContent(
                type = detectedType,
                title = subject.ifBlank { "Shared ${detectedType.name.lowercase().replaceFirstChar { it.uppercase() }}" },
                uri = streamUri,
                filePath = streamUri.toString(),
                notes = accompanyingText.ifBlank { null }
            )
        }

        // 3. Handle Text / URL Payloads (EXTRA_TEXT or ClipData text)
        var rawText = accompanyingText
        if (rawText.isBlank() && intent.clipData != null && intent.clipData!!.itemCount > 0) {
            rawText = intent.clipData!!.getItemAt(0).text?.toString() ?: ""
        }

        if (rawText.isNotBlank()) {
            val matcher = URL_REGEX.matcher(rawText)
            if (matcher.find()) {
                val extractedUrl = matcher.group()
                val normalizedUrl = UrlNormalizer.normalize(extractedUrl)
                val remainingNote = rawText.replace(extractedUrl, "").trim()
                val detectedSource = UrlNormalizer.detectSourceApp(extractedUrl)
                val defaultTitle = when (detectedSource) {
                    "Instagram" -> "Instagram Post"
                    "YouTube" -> "YouTube Video"
                    "Twitter / X" -> "X Post"
                    "Reddit" -> "Reddit Post"
                    else -> subject.ifBlank { detectedSource ?: "Shared Link" }
                }

                return CapturedContent(
                    type = ContentType.LINK,
                    title = subject.ifBlank { defaultTitle },
                    url = normalizedUrl,
                    originalUrl = extractedUrl,
                    sourceApp = detectedSource,
                    notes = remainingNote.ifBlank { null }
                )
            } else {
                return CapturedContent(
                    type = ContentType.NOTE,
                    title = subject.ifBlank { rawText.take(40) + if (rawText.length > 40) "..." else "" },
                    notes = rawText
                )
            }
        }

        return null
    }

    private fun classifyMime(mimeType: String, pathOrUri: String): ContentType {
        val lowerMime = mimeType.lowercase()
        val lowerPath = pathOrUri.lowercase()

        return when {
            lowerMime.startsWith("image/") || lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") ||
                    lowerPath.endsWith(".png") || lowerPath.endsWith(".webp") || lowerPath.endsWith(".gif") -> ContentType.IMAGE

            lowerMime.startsWith("video/") || lowerPath.endsWith(".mp4") || lowerPath.endsWith(".mkv") ||
                    lowerPath.endsWith(".mov") || lowerPath.endsWith(".webm") -> ContentType.VIDEO

            lowerMime.startsWith("audio/") || lowerPath.endsWith(".mp3") || lowerPath.endsWith(".m4a") ||
                    lowerPath.endsWith(".wav") || lowerPath.endsWith(".aac") -> ContentType.AUDIO

            lowerMime == "application/pdf" || lowerPath.endsWith(".pdf") -> ContentType.PDF

            else -> ContentType.DOCUMENT
        }
    }
}
