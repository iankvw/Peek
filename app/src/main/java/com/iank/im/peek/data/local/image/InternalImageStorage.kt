package com.iank.im.peek.data.local.image

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.iank.im.peek.domain.model.ImagePayloadFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InternalImageStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val imagesRootDir: File by lazy {
        File(context.filesDir, IMAGES_ROOT_DIR_NAME).apply { mkdirs() }
    }

    private val shareCacheDir: File by lazy {
        File(context.cacheDir, SHARE_CACHE_DIR_NAME).apply { mkdirs() }
    }

    fun ensureDirectories() {
        listOf(SUBDIR_STAGING, SUBDIR_ORIGINAL, SUBDIR_PAYLOAD, SUBDIR_THUMB).forEach { sub ->
            File(imagesRootDir, sub).mkdirs()
        }
        shareCacheDir.mkdirs()
    }

    fun resolve(relPath: String): File {
        val cleaned = relPath.trimStart('/')
        require(cleaned.isNotBlank() && !cleaned.contains("..")) {
            "Invalid relative image path: $relPath"
        }
        val target = File(imagesRootDir, cleaned)
        val rootCanonical = imagesRootDir.canonicalFile
        val targetCanonical = target.canonicalFile
        require(
            targetCanonical.path.startsWith(rootCanonical.path + File.separator) ||
                targetCanonical == rootCanonical
        ) {
            "Path escapes image root: $relPath"
        }
        return target
    }

    fun resolveIfExists(relPath: String?): File? {
        if (relPath.isNullOrBlank()) return null
        val file = runCatching { resolve(relPath) }.getOrNull() ?: return null
        return file.takeIf { it.exists() && it.isFile }
    }

    fun createStagingOriginalRelPath(uuid: String, extension: String): String {
        val ext = extension.lowercase().ifBlank { "jpg" }
        return "$SUBDIR_STAGING/$uuid.$ext"
    }

    fun createStagingThumbRelPath(uuid: String): String =
        "$SUBDIR_STAGING/${uuid}_thumb.webp"

    fun createOriginalRelPath(uuid: String, extension: String): String {
        val ext = extension.lowercase().ifBlank { "jpg" }
        return "$SUBDIR_ORIGINAL/$uuid.$ext"
    }

    fun createThumbRelPath(uuid: String): String =
        "$SUBDIR_THUMB/$uuid.webp"

    fun createPayloadRelPath(
        uuid: String,
        maxSide: Int,
        revision: Int,
        format: ImagePayloadFormat
    ): String = "$SUBDIR_PAYLOAD/${uuid}_${maxSide}_r${revision}.${format.fileExtension}"

    fun writeAtomically(targetRelPath: String, writer: (OutputStream) -> Unit): File {
        val target = resolve(targetRelPath)
        val parent = target.parentFile ?: imagesRootDir
        parent.mkdirs()

        val tempFile = File(parent, "${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(tempFile).use { fos ->
                writer(fos)
                fos.flush()
                fos.fd.sync()
            }
            if (target.exists() && !target.delete()) {
                throw IOException("Failed to remove existing file: ${target.absolutePath}")
            }
            if (!tempFile.renameTo(target)) {
                throw IOException("Atomic rename failed: ${tempFile.name} -> ${target.name}")
            }
            return target
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun moveAtomically(sourceRelPath: String, targetRelPath: String): Boolean {
        val source = resolve(sourceRelPath)
        val target = resolve(targetRelPath)
        if (!source.exists()) return false
        target.parentFile?.mkdirs()

        if (target.exists()) {
            target.delete()
        }
        if (source.renameTo(target)) {
            return true
        }
        return try {
            writeAtomically(targetRelPath) { out ->
                source.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            source.delete()
            true
        } catch (_: IOException) {
            false
        }
    }

    fun deleteRelPath(relPath: String): Boolean {
        val file = runCatching { resolve(relPath) }.getOrNull() ?: return true
        if (!file.exists()) return true
        return file.delete()
    }

    fun copyToShareCacheAndGetUri(relPath: String, mimeType: String): Uri? {
        val source = resolveIfExists(relPath) ?: return null
        shareCacheDir.mkdirs()
        cleanupOldShareFiles()

        val ext = when {
            mimeType.equals("image/webp", ignoreCase = true) -> "webp"
            mimeType.equals("image/png", ignoreCase = true) -> "png"
            mimeType.equals("image/heic", ignoreCase = true) -> "heic"
            else -> source.extension.ifBlank { "jpg" }
        }
        val destFile = File(shareCacheDir, "peek_share_${System.currentTimeMillis()}.$ext")
        return try {
            source.inputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )
        } catch (_: Exception) {
            destFile.delete()
            null
        }
    }

    fun calculateTotalImageStorageBytes(): Long {
        if (!imagesRootDir.exists()) return 0L
        return imagesRootDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    fun listStagingFiles(): List<Pair<String, File>> = listSubdirFiles(SUBDIR_STAGING)

    fun listCommittedImageFiles(): List<Pair<String, File>> =
        listSubdirFiles(SUBDIR_ORIGINAL) +
            listSubdirFiles(SUBDIR_PAYLOAD) +
            listSubdirFiles(SUBDIR_THUMB)

    private fun listSubdirFiles(subdir: String): List<Pair<String, File>> {
        val dir = File(imagesRootDir, subdir)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.map { file -> "$subdir/${file.name}" to file }
            .orEmpty()
    }

    private fun cleanupOldShareFiles() {
        val cutoff = System.currentTimeMillis() - SHARE_CACHE_MAX_AGE_MS
        shareCacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    companion object {
        private const val IMAGES_ROOT_DIR_NAME = "images"
        private const val SHARE_CACHE_DIR_NAME = "share"
        const val SUBDIR_STAGING = "staging"
        const val SUBDIR_ORIGINAL = "original"
        const val SUBDIR_PAYLOAD = "payload"
        const val SUBDIR_THUMB = "thumb"
        private const val SHARE_CACHE_MAX_AGE_MS = 60L * 60L * 1000L
    }
}
