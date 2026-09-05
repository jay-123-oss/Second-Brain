package com.example.brain.util

import android.net.Uri

object UrlNormalizer {

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "msclkid", "mc_eid", "igshid", "ref", "ref_src",
        "_hsenc", "_hsmi", "mkt_tok", "spm", "source"
    )

    /**
     * Normalizes a URL:
     * - Ensures valid scheme (defaults to https if missing)
     * - Strips marketing, analytics, and social tracking query parameters
     * - Canonicalizes lowercase host
     * - Strips empty hash fragments and trailing slashes for clean root domains
     */
    fun normalize(rawUrl: String): String {
        var trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ""

        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed = "https://$trimmed"
        }

        try {
            val uri = Uri.parse(trimmed)
            val scheme = uri.scheme?.lowercase() ?: "https"
            val host = uri.host?.lowercase()

            if (!host.isNullOrBlank()) {
                val queryParameterNames = uri.queryParameterNames
                val builder = uri.buildUpon()
                    .scheme(scheme)
                    .authority(host)
                    .clearQuery()

                for (param in queryParameterNames) {
                    if (param.lowercase() !in TRACKING_PARAMS) {
                        val values = uri.getQueryParameters(param)
                        for (v in values) {
                            builder.appendQueryParameter(param, v)
                        }
                    }
                }

                var result = builder.build().toString()
                if (result.endsWith("#")) {
                    result = result.removeSuffix("#")
                }
                if (result.endsWith("/") && uri.path.isNullOrEmpty() && uri.query.isNullOrEmpty()) {
                    result = result.removeSuffix("/")
                }
                return result
            }
        } catch (_: Throwable) {}

        // Pure Java standard URI fallback (vital for JVM unit testing and robust parsing)
        return try {
            val javaUri = java.net.URI(trimmed)
            val scheme = javaUri.scheme?.lowercase() ?: "https"
            val host = javaUri.host?.lowercase() ?: return trimmed
            val path = javaUri.path ?: ""
            val rawQuery = javaUri.query
            val filteredQuery = if (!rawQuery.isNullOrBlank()) {
                rawQuery.split("&")
                    .filter { param ->
                        val key = param.substringBefore("=").lowercase()
                        key !in TRACKING_PARAMS
                    }
                    .joinToString("&")
            } else ""

            var res = "$scheme://$host$path"
            if (filteredQuery.isNotBlank()) {
                res += "?$filteredQuery"
            }
            if (res.endsWith("/")) res.removeSuffix("/") else res
        } catch (_: Throwable) {
            trimmed
        }
    }

    /**
     * Extracts a friendly human-readable source app name from the URL or host.
     */
    fun detectSourceApp(rawUrl: String): String? {
        val lower = rawUrl.lowercase()
        return when {
            lower.contains("instagram.com") -> "Instagram"
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube"
            lower.contains("twitter.com") || lower.contains("x.com") -> "Twitter / X"
            lower.contains("reddit.com") || lower.contains("redd.it") -> "Reddit"
            lower.contains("github.com") -> "GitHub"
            lower.contains("linkedin.com") -> "LinkedIn"
            lower.contains("medium.com") -> "Medium"
            lower.contains("substack.com") -> "Substack"
            lower.contains("spotify.com") -> "Spotify"
            lower.contains("tiktok.com") -> "TikTok"
            lower.contains("threads.net") -> "Threads"
            lower.contains("wikipedia.org") -> "Wikipedia"
            lower.contains("nytimes.com") -> "New York Times"
            lower.contains("theverge.com") -> "The Verge"
            else -> extractDomain(rawUrl)
        }
    }

    /**
     * Extracts the clean domain from a URL (e.g. "example.com").
     */
    fun extractDomain(rawUrl: String): String? {
        val trimmed = if (!rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true)) {
            "https://$rawUrl"
        } else rawUrl

        return try {
            val host = Uri.parse(trimmed)?.host
            if (!host.isNullOrBlank()) {
                return host.removePrefix("www.")
            }
            java.net.URI(trimmed).host?.removePrefix("www.")
        } catch (_: Throwable) {
            try {
                java.net.URI(trimmed).host?.removePrefix("www.")
            } catch (_: Throwable) {
                null
            }
        }
    }
}
