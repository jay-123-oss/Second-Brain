package com.example.brain.data.model

import android.net.Uri

enum class ContentType {
    LINK,
    NOTE,
    IMAGE,
    VIDEO,
    PDF,
    AUDIO,
    DOCUMENT
}

data class CapturedContent(
    val type: ContentType = ContentType.NOTE,
    val title: String = "",
    val url: String? = null,
    val originalUrl: String? = null,
    val sourceApp: String? = null,
    val notes: String? = null,
    val filePath: String? = null,
    val uri: Uri? = null,
    val uris: List<Uri>? = null
)
