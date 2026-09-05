package com.example.brain.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import com.example.brain.data.model.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ExtractedMediaInfo(
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null,
    val fileSize: Long = 0L,
    val thumbnailPath: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val pageCount: Int? = null,
    val extractedText: String? = null
)

/**
 * On-device media intelligence processor.
 * Extracts duration, dimensions, metadata, text, and generates optimized thumbnails
 * for video, audio, image, PDF, and generic text documents.
 * Never performs fake OCR or fake transcription.
 */
object MediaProcessor {

    private const val MAX_THUMBNAIL_DIMENSION = 512

    suspend fun processMedia(
        context: Context,
        file: File,
        contentType: ContentType,
        contentHash: String
    ): ExtractedMediaInfo = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            return@withContext ExtractedMediaInfo(fileSize = 0L)
        }

        when (contentType) {
            ContentType.VIDEO -> processVideo(context, file, contentHash)
            ContentType.IMAGE -> processImage(context, file, contentHash)
            ContentType.AUDIO -> processAudio(file)
            ContentType.PDF -> processPdf(context, file, contentHash)
            ContentType.DOCUMENT, ContentType.NOTE -> processDocument(file)
            ContentType.LINK -> ExtractedMediaInfo(fileSize = file.length())
        }
    }

    private fun processVideo(context: Context, file: File, hash: String): ExtractedMediaInfo {
        val retriever = MediaMetadataRetriever()
        var durationMs: Long? = null
        var width: Int? = null
        var height: Int? = null
        var mime: String? = null
        var thumbPath: String? = null

        try {
            retriever.setDataSource(file.absolutePath)

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                durationMs = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.let {
                width = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?.let {
                height = it
            }
            mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            // Extract frame at 1s, or first frame as fallback
            val frameBitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0)

            if (frameBitmap != null) {
                thumbPath = saveOptimizedThumbnail(context, frameBitmap, hash)
                frameBitmap.recycle()
            }
        } catch (_: Exception) {
            // Graceful partial failure - never crash
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return ExtractedMediaInfo(
            durationMs = durationMs,
            width = width,
            height = height,
            mimeType = mime ?: "video/mp4",
            fileSize = file.length(),
            thumbnailPath = thumbPath
        )
    }

    private fun processImage(context: Context, file: File, hash: String): ExtractedMediaInfo {
        var width: Int? = null
        var height: Int? = null
        var mime: String? = null
        var thumbPath: String? = null

        try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            width = boundsOptions.outWidth
            height = boundsOptions.outHeight
            mime = boundsOptions.outMimeType

            // Generate downsampled thumbnail
            val sampleSize = calculateInSampleSize(boundsOptions, MAX_THUMBNAIL_DIMENSION, MAX_THUMBNAIL_DIMENSION)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val sampledBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            if (sampledBitmap != null) {
                thumbPath = saveOptimizedThumbnail(context, sampledBitmap, hash)
                sampledBitmap.recycle()
            }
        } catch (_: Exception) {}

        return ExtractedMediaInfo(
            width = width,
            height = height,
            mimeType = mime ?: "image/jpeg",
            fileSize = file.length(),
            thumbnailPath = thumbPath
        )
    }

    private fun processAudio(file: File): ExtractedMediaInfo {
        val retriever = MediaMetadataRetriever()
        var durationMs: Long? = null
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var mime: String? = null

        try {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return ExtractedMediaInfo(
            durationMs = durationMs,
            title = title,
            artist = artist,
            album = album,
            mimeType = mime ?: "audio/mpeg",
            fileSize = file.length()
        )
    }

    private fun processPdf(context: Context, file: File, hash: String): ExtractedMediaInfo {
        var pageCount: Int? = null
        var thumbPath: String? = null

        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            pageCount = renderer.pageCount

            if (pageCount > 0) {
                val page = renderer.openPage(0)
                val scaleFactor = MAX_THUMBNAIL_DIMENSION.toFloat() / maxOf(page.width, page.height)
                val destWidth = (page.width * scaleFactor).toInt().coerceAtLeast(1)
                val destHeight = (page.height * scaleFactor).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                thumbPath = saveOptimizedThumbnail(context, bitmap, hash)
                bitmap.recycle()
                page.close()
            }

            renderer.close()
            pfd.close()
        } catch (_: Exception) {}

        return ExtractedMediaInfo(
            pageCount = pageCount,
            mimeType = "application/pdf",
            fileSize = file.length(),
            thumbnailPath = thumbPath
        )
    }

    private fun processDocument(file: File): ExtractedMediaInfo {
        val extension = file.extension.lowercase()
        val textExtensions = setOf("txt", "md", "csv", "json", "xml", "html", "htm", "log")
        var extractedText: String? = null

        if (extension in textExtensions) {
            try {
                // Read up to first 10,000 characters for search indexing
                extractedText = file.bufferedReader().use { reader ->
                    val chars = CharArray(10_000)
                    val read = reader.read(chars)
                    if (read > 0) String(chars, 0, read) else null
                }
            } catch (_: Exception) {}
        }

        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"

        return ExtractedMediaInfo(
            mimeType = mime,
            fileSize = file.length(),
            extractedText = extractedText
        )
    }

    private fun saveOptimizedThumbnail(context: Context, bitmap: Bitmap, hash: String): String? {
        return try {
            val thumbDir = File(context.filesDir, "thumbnails").apply {
                if (!exists()) mkdirs()
            }
            val shortHash = if (hash.length >= 16) hash.take(16) else "${hash}_${System.currentTimeMillis()}"
            val thumbFile = File(thumbDir, "thumb_$shortHash.webp")

            if (thumbFile.exists() && thumbFile.length() > 0) {
                return thumbFile.absolutePath
            }

            // Scale down if needed
            val maxDim = maxOf(bitmap.width, bitmap.height)
            val scaledBitmap = if (maxDim > MAX_THUMBNAIL_DIMENSION) {
                val scale = MAX_THUMBNAIL_DIMENSION.toFloat() / maxDim
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

            FileOutputStream(thumbFile).use { out ->
                @Suppress("DEPRECATION")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    scaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out)
                } else {
                    scaledBitmap.compress(Bitmap.CompressFormat.WEBP, 80, out)
                }
                out.flush()
            }

            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }

            thumbFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
