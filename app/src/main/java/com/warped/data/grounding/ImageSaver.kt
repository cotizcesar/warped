package com.warped.data.grounding

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

/**
 * Quick-task (image-grid): gallery download for grounded image results.
 *
 * Fetch precedent follows `ModelDownloadWorker` (bare OkHttp GET on IO —
 * never the authed endpoint client, so endpoint keys can never leak to
 * image hosts); the DESTINATION differs (gallery, not app-private):
 * MediaStore `Images/Media` on Q+ with `RELATIVE_PATH Pictures/Warped`
 * (no storage permission needed for own insertions), plain MediaStore
 * insert pre-Q (needs the legacy write permission when enforced).
 *
 * Plain class (not Hilt): constructed with `remember` at the single grid
 * call site. Compose/Android-framework code — on-device confirmation
 * required (MediaStore write, gallery visibility, permission UX).
 */
class ImageSaver(
    private val client: OkHttpClient = OkHttpClient(),
) {

    sealed interface SaveResult {
        data object Saved : SaveResult
        data object Failed : SaveResult
    }

    suspend fun saveToGallery(context: Context, imageUrl: String): SaveResult =
        withContext(Dispatchers.IO) {
            val trimmed = imageUrl.trim()
            if (!trimmed.startsWith("http://", ignoreCase = true) &&
                !trimmed.startsWith("https://", ignoreCase = true)
            ) {
                return@withContext SaveResult.Failed
            }
            try {
                val request = Request.Builder()
                    .url(trimmed)
                    .header("User-Agent", WebPageFetcher.USER_AGENT)
                    .header("Accept", "image/*")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Image download failed with HTTP ${response.code}")
                    }
                    val body = response.body
                        ?: throw IOException("Image download returned an empty body")
                    val mime = body.contentType()?.toString()
                        ?.substringBefore(';')?.trim()
                        ?.takeIf { it.startsWith("image/") }
                        ?: "image/jpeg"
                    // Bounded read: a hostile image host must not be able
                    // to OOM the app with a multi-GB stream.
                    val bytes = readBounded(body)
                    if (bytes.isEmpty()) throw IOException("Image download returned zero bytes")
                    writeToMediaStore(context, bytes, mime, trimmed)
                }
                SaveResult.Saved
            } catch (e: Exception) {
                Timber.e(e, "ImageSaver: gallery save failed")
                SaveResult.Failed
            }
        }

    private fun writeToMediaStore(
        context: Context,
        bytes: ByteArray,
        mime: String,
        sourceUrl: String,
    ) {
        val fileName = fileNameFor(sourceUrl, mime)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Warped",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ) ?: throw IOException("MediaStore insert returned null")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            } ?: throw IOException("MediaStore output stream is null")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
                // Best-effort orphan cleanup — the original error propagates.
            }
            throw e
        }
    }

    companion object {
        /** Gallery download cap — refuses larger images before MediaStore. */
        const val MAX_IMAGE_BYTES = 20 * 1024 * 1024

        /**
         * Read at most [MAX_IMAGE_BYTES]+1 bytes; throws when the stream
         * exceeds the cap (the +1 distinguishes exact-cap from over-cap).
         */
        private fun readBounded(body: okhttp3.ResponseBody): ByteArray {
            val buffer = okio.Buffer()
            var remaining = MAX_IMAGE_BYTES + 1L
            val source = body.source()
            while (remaining > 0) {
                val read = source.read(buffer, minOf(remaining, 8192L))
                if (read == -1L) break
                remaining -= read
            }
            if (remaining == 0L) throw IOException("Image exceeds 20MB limit")
            return buffer.readByteArray()
        }
        /**
         * Deterministic gallery file name from the source URL + MIME type.
         * Pure Kotlin — JVM-testable without Android.
         */
        fun fileNameFor(sourceUrl: String, mime: String): String {
            val extension = when (mime.substringBefore(';').trim().lowercase()) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "image/avif" -> "avif"
                else -> "jpg"
            }
            val slug = sourceUrl
                .substringAfterLast('/')
                .substringBefore('?')
                .replace(Regex("[^A-Za-z0-9]+"), "-")
                .trim('-')
                .take(32)
                .ifEmpty { "image" }
            return "warped-$slug.$extension"
        }
    }
}
