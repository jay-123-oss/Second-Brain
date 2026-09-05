package com.example.brain.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URI

/**
 * Result data class for fetched web metadata.
 */
data class WebMetadata(
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val siteName: String?,
    val canonicalUrl: String?,
    val extractedText: String? = null,
    val author: String? = null,
    val publicationDate: String? = null
)

/**
 * Robust, timeout-resilient OpenGraph and HTML meta scraper using Jsoup.
 * Extracts: og:title, og:description, og:image, og:site_name, twitter:image, meta description, page title,
 * author, publication date, and cleaned body text for deep search indexing.
 */
object MetadataFetcher {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    private const val TIMEOUT_MS = 6000

    suspend fun fetch(url: String): Result<WebMetadata> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }

            val doc = Jsoup.connect(normalizedUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get()

            // 1. Title Extraction
            val ogTitle = doc.select("meta[property=og:title]").attr("content").takeIf { it.isNotBlank() }
            val twitterTitle = doc.select("meta[name=twitter:title]").attr("content").takeIf { it.isNotBlank() }
            val htmlTitle = doc.title().takeIf { it.isNotBlank() }
            val finalTitle = ogTitle ?: twitterTitle ?: htmlTitle ?: extractDomainName(normalizedUrl)

            // 2. Description Extraction
            val ogDesc = doc.select("meta[property=og:description]").attr("content").takeIf { it.isNotBlank() }
            val metaDesc = doc.select("meta[name=description]").attr("content").takeIf { it.isNotBlank() }
            val twitterDesc = doc.select("meta[name=twitter:description]").attr("content").takeIf { it.isNotBlank() }
            val finalDesc = ogDesc ?: twitterDesc ?: metaDesc

            // 3. Image URL Extraction
            val ogImage = doc.select("meta[property=og:image]").attr("content").takeIf { it.isNotBlank() }
            val twitterImage = doc.select("meta[name=twitter:image]").attr("content").takeIf { it.isNotBlank() }
            val finalImage = resolveRelativeUrl(normalizedUrl, ogImage ?: twitterImage)

            // 4. Site Name
            val ogSiteName = doc.select("meta[property=og:site_name]").attr("content").takeIf { it.isNotBlank() }
            val finalSiteName = ogSiteName ?: extractDomainName(normalizedUrl)

            // 5. Canonical URL
            val canonical = doc.select("link[rel=canonical]").attr("href").takeIf { it.isNotBlank() }
                ?: doc.select("meta[property=og:url]").attr("content").takeIf { it.isNotBlank() }

            // 6. Author and Publication Date
            val author = doc.select("meta[name=author]").attr("content").takeIf { it.isNotBlank() }
                ?: doc.select("meta[property=article:author]").attr("content").takeIf { it.isNotBlank() }
            val pubDate = doc.select("meta[property=article:published_time]").attr("content").takeIf { it.isNotBlank() }
                ?: doc.select("meta[name=date]").attr("content").takeIf { it.isNotBlank() }

            // 7. Cleaned Body Text Extraction (for FTS Deep Search, capped to 10,000 chars)
            val bodyText = try {
                val body = doc.body()
                // Remove scripts, styles, and navbars
                body.select("script, style, nav, footer, header, noscript, svg").remove()
                val text = body.text().trim()
                if (text.length > 10_000) text.substring(0, 10_000) else text.ifBlank { null }
            } catch (_: Exception) {
                null
            }

            Result.success(
                WebMetadata(
                    title = finalTitle,
                    description = finalDesc,
                    imageUrl = finalImage,
                    siteName = finalSiteName,
                    canonicalUrl = canonical ?: normalizedUrl,
                    extractedText = bodyText,
                    author = author,
                    publicationDate = pubDate
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractDomainName(url: String): String {
        return try {
            val uri = URI(url)
            val domain = uri.host ?: url
            domain.removePrefix("www.")
        } catch (_: Exception) {
            url
        }
    }

    private fun resolveRelativeUrl(baseUrl: String, imgUrl: String?): String? {
        if (imgUrl.isNullOrBlank()) return null
        if (imgUrl.startsWith("http://") || imgUrl.startsWith("https://")) return imgUrl
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(imgUrl).toString()
        } catch (_: Exception) {
            imgUrl
        }
    }
}
