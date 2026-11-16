package com.iank.im.peek.wear.data.cache

import android.content.Context
import com.iank.im.peek.wear.domain.model.WearImageQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearImageCache @Inject constructor(
    @param:ApplicationContext private val appContext: Context
) {
    private val cacheRootDir: File by lazy {
        File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    }

    private val _cacheUpdates = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val cacheUpdates: SharedFlow<String> = _cacheUpdates.asSharedFlow()

    fun thumbnailKey(attachmentId: String): String =
        "thumb_${sanitizeKey(attachmentId)}.webp"

    fun fullImageKey(attachmentId: String, quality: WearImageQuality): String =
        "full_${sanitizeKey(attachmentId)}_${quality.name}.webp"

    fun getThumbnailFile(attachmentId: String): File? =
        resolveExistingFile(thumbnailKey(attachmentId))

    fun getFullImageFile(attachmentId: String, quality: WearImageQuality): File? =
        resolveExistingFile(fullImageKey(attachmentId, quality))

    fun getBestAvailablePreview(attachmentId: String, preferredQuality: WearImageQuality): File? {
        getFullImageFile(attachmentId, preferredQuality)?.let { return it }
        for (quality in WearImageQuality.entries) {
            getFullImageFile(attachmentId, quality)?.let { return it }
        }
        return getThumbnailFile(attachmentId)
    }

    private fun resolveExistingFile(cacheKey: String): File? {
        val file = File(cacheRootDir, cacheKey)
        return file.takeIf { it.exists() && it.isFile && it.length() > 0L }
    }

    suspend fun saveFromStream(cacheKey: String, input: InputStream): File = withContext(Dispatchers.IO) {
        cacheRootDir.mkdirs()
        val targetFile = File(cacheRootDir, cacheKey)
        val tempFile = File(cacheRootDir, "$cacheKey.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(tempFile).use { out ->
                val buffer = ByteArray(STREAM_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                }
                out.flush()
                out.fd.sync()
            }
            if (tempFile.length() <= 0L) {
                throw IOException("Received empty image stream for $cacheKey")
            }
            if (targetFile.exists() && !targetFile.delete()) {
                throw IOException("Failed to overwrite cached image $cacheKey")
            }
            if (!tempFile.renameTo(targetFile)) {
                throw IOException("Failed to rename cached image $cacheKey")
            }
            trimCacheIfNeeded()
            _cacheUpdates.tryEmit(cacheKey)
            targetFile
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun trimCacheIfNeeded() {
        val files = cacheRootDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= MAX_WATCH_CACHE_BYTES) return
        val oldestFirst = files.sortedBy { it.lastModified() }
        for (file in oldestFirst) {
            if (totalBytes <= MAX_WATCH_CACHE_BYTES) break
            val size = file.length()
            if (file.delete()) {
                totalBytes -= size
            }
        }
    }

    private fun sanitizeKey(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_-]"), "_")

    companion object {
        private const val CACHE_DIR_NAME = "wear_images"
        private const val STREAM_BUFFER_BYTES = 16 * 1024
        private const val MAX_WATCH_CACHE_BYTES = 32L * 1024L * 1024L
    }
}
