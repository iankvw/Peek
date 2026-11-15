package com.iank.im.peek.data.local.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import com.iank.im.peek.domain.model.ImagePayloadFormat
import com.iank.im.peek.domain.model.ImageResolutionPreset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

sealed interface StageImageResult {
    data class Success(
        val id: String,
        val stagingOriginalRelPath: String,
        val stagingThumbnailRelPath: String,
        val mimeType: String,
        val width: Int,
        val height: Int
    ) : StageImageResult

    data object FileTooLarge : StageImageResult
    data object UnsupportedFormat : StageImageResult
    data class Failed(val cause: Throwable? = null) : StageImageResult
}

data class EncodedPayloadResult(
    val payloadRelPath: String,
    val actualMaxSide: Int,
    val fileSizeBytes: Long
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ImagePreprocessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storage: InternalImageStorage
) {
    private val contentResolver: ContentResolver = context.contentResolver
    private val processingDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val processingSemaphore = Semaphore(1)

    suspend fun stageFromCameraFile(stagingOriginalRelPath: String, id: String): StageImageResult =
        withContext(processingDispatcher) {
            processingSemaphore.withPermit {
                val originalFile = storage.resolveIfExists(stagingOriginalRelPath)
                    ?: return@withPermit StageImageResult.Failed(IOException("Captured file missing"))
                val mimeType = detectMimeType(originalFile) ?: "image/jpeg"
                val (width, height) = readOrientedDimensions(originalFile)
                if (width <= 0 || height <= 0) {
                    storage.deleteRelPath(stagingOriginalRelPath)
                    return@withPermit StageImageResult.UnsupportedFormat
                }
                val thumbRelPath = storage.createStagingThumbRelPath(id)
                return@withPermit try {
                    generateScaledImage(
                        sourceFile = originalFile,
                        targetRelPath = thumbRelPath,
                        targetMaxSide = THUMBNAIL_MAX_SIDE_PX,
                        format = ImagePayloadFormat.WEBP,
                        quality = THUMBNAIL_WEBP_QUALITY
                    )
                    StageImageResult.Success(
                        id = id,
                        stagingOriginalRelPath = stagingOriginalRelPath,
                        stagingThumbnailRelPath = thumbRelPath,
                        mimeType = mimeType,
                        width = width,
                        height = height
                    )
                } catch (t: Throwable) {
                    storage.deleteRelPath(stagingOriginalRelPath)
                    storage.deleteRelPath(thumbRelPath)
                    StageImageResult.Failed(t)
                }
            }
        }

    suspend fun stageFromGalleryUri(uri: Uri): StageImageResult =
        withContext(processingDispatcher) {
            processingSemaphore.withPermit {
                val mimeType = contentResolver.getType(uri)?.lowercase()
                    ?: guessMimeFromUri(uri)
                if (mimeType == null || mimeType !in ALLOWED_MIME_TYPES) {
                    return@withPermit StageImageResult.UnsupportedFormat
                }

                val reportedSize = queryUriSizeBytes(uri)
                if (reportedSize != null && reportedSize > MAX_STAGING_FILE_BYTES) {
                    return@withPermit StageImageResult.FileTooLarge
                }

                val id = UUID.randomUUID().toString()
                val extension = extensionForMime(mimeType)
                val stagingOriginalRelPath = storage.createStagingOriginalRelPath(id, extension)
                val stagingThumbRelPath = storage.createStagingThumbRelPath(id)

                return@withPermit try {
                    var copiedBytes = 0L
                    val originalFile = storage.writeAtomically(stagingOriginalRelPath) { out ->
                        contentResolver.openInputStream(uri)?.use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                copiedBytes += read
                                if (copiedBytes > MAX_STAGING_FILE_BYTES) {
                                    throw FileSizeLimitExceededException()
                                }
                                out.write(buffer, 0, read)
                            }
                        } ?: throw IOException("Unable to open gallery URI stream")
                    }

                    val (width, height) = readOrientedDimensions(originalFile)
                    if (width <= 0 || height <= 0) {
                        storage.deleteRelPath(stagingOriginalRelPath)
                        return@withPermit StageImageResult.UnsupportedFormat
                    }

                    generateScaledImage(
                        sourceFile = originalFile,
                        targetRelPath = stagingThumbRelPath,
                        targetMaxSide = THUMBNAIL_MAX_SIDE_PX,
                        format = ImagePayloadFormat.WEBP,
                        quality = THUMBNAIL_WEBP_QUALITY
                    )

                    StageImageResult.Success(
                        id = id,
                        stagingOriginalRelPath = stagingOriginalRelPath,
                        stagingThumbnailRelPath = stagingThumbRelPath,
                        mimeType = mimeType,
                        width = width,
                        height = height
                    )
                } catch (_: FileSizeLimitExceededException) {
                    storage.deleteRelPath(stagingOriginalRelPath)
                    storage.deleteRelPath(stagingThumbRelPath)
                    StageImageResult.FileTooLarge
                } catch (t: Throwable) {
                    storage.deleteRelPath(stagingOriginalRelPath)
                    storage.deleteRelPath(stagingThumbRelPath)
                    StageImageResult.Failed(t)
                }
            }
        }

    suspend fun createPayload(
        attachmentId: String,
        originalRelPath: String,
        targetMaxSide: Int,
        revision: Int,
        format: ImagePayloadFormat
    ): EncodedPayloadResult = withContext(processingDispatcher) {
        processingSemaphore.withPermit {
            val sourceFile = storage.resolveIfExists(originalRelPath)
                ?: throw IOException("Original image missing: $originalRelPath")
            val clampedMaxSide = targetMaxSide.coerceIn(MIN_PAYLOAD_SIDE_PX, ImageResolutionPreset.MAX_WEBP_DIMENSION)
            val payloadRelPath = storage.createPayloadRelPath(
                uuid = attachmentId,
                maxSide = clampedMaxSide,
                revision = revision,
                format = format
            )
            val quality = when (format) {
                ImagePayloadFormat.WEBP -> PAYLOAD_WEBP_QUALITY
                ImagePayloadFormat.JPEG -> PAYLOAD_JPEG_QUALITY
            }
            val writtenFile = generateScaledImage(
                sourceFile = sourceFile,
                targetRelPath = payloadRelPath,
                targetMaxSide = clampedMaxSide,
                format = format,
                quality = quality
            )
            EncodedPayloadResult(
                payloadRelPath = payloadRelPath,
                actualMaxSide = clampedMaxSide,
                fileSizeBytes = writtenFile.length()
            )
        }
    }

    private fun generateScaledImage(
        sourceFile: File,
        targetRelPath: String,
        targetMaxSide: Int,
        format: ImagePayloadFormat,
        quality: Int
    ): File {
        val bitmap = decodeScaledBitmapSafely(sourceFile, targetMaxSide)
        try {
            return storage.writeAtomically(targetRelPath) { out ->
                val compressFormat = resolveCompressFormat(format)
                if (!bitmap.compress(compressFormat, quality, out)) {
                    throw IOException("Failed to compress bitmap as $format")
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeScaledBitmapSafely(sourceFile: File, maxSideLimit: Int): Bitmap {
        val clampedLimit = maxSideLimit.coerceIn(1, ImageResolutionPreset.MAX_WEBP_DIMENSION)
        return try {
            decodeWithImageDecoder(sourceFile, clampedLimit)
        } catch (oom: OutOfMemoryError) {
            decodeWithBitmapFactoryFallback(sourceFile, (clampedLimit / 2).coerceAtLeast(512), extraSampleMultiplier = 2)
        } catch (_: Throwable) {
            decodeWithBitmapFactoryFallback(sourceFile, clampedLimit, extraSampleMultiplier = 1)
        }
    }

    private fun decodeWithImageDecoder(sourceFile: File, maxSideLimit: Int): Bitmap {
        val source = ImageDecoder.createSource(sourceFile)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false

            val naturalW = info.size.width.coerceAtLeast(1)
            val naturalH = info.size.height.coerceAtLeast(1)
            val (targetW, targetH) = computeHeapSafeTargetDimensions(
                naturalWidth = naturalW,
                naturalHeight = naturalH,
                maxSideLimit = maxSideLimit
            )
            if (targetW != naturalW || targetH != naturalH) {
                decoder.setTargetSize(targetW, targetH)
            }
        }
    }

    private fun decodeWithBitmapFactoryFallback(
        sourceFile: File,
        maxSideLimit: Int,
        extraSampleMultiplier: Int
    ): Bitmap {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOpts)
        val rawW = boundsOpts.outWidth.coerceAtLeast(1)
        val rawH = boundsOpts.outHeight.coerceAtLeast(1)

        val orientation = readExifOrientation(sourceFile)
        val isSwapped = isOrientationAxisSwapped(orientation)
        val uprightW = if (isSwapped) rawH else rawW
        val uprightH = if (isSwapped) rawW else rawH

        val (targetW, targetH) = computeHeapSafeTargetDimensions(
            naturalWidth = uprightW,
            naturalHeight = uprightH,
            maxSideLimit = maxSideLimit
        )

        var sampleSize = 1
        while ((uprightW / (sampleSize * 2)) >= targetW && (uprightH / (sampleSize * 2)) >= targetH) {
            sampleSize *= 2
        }
        sampleSize *= extraSampleMultiplier.coerceAtLeast(1)

        // Ensure sampled bitmap fits comfortably in heap
        while (estimatedRgbaBytes(rawW / sampleSize, rawH / sampleSize) > availableHeapBudgetBytes()) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampledBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOpts)
            ?: throw IOException("BitmapFactory failed to decode ${sourceFile.name}")

        val matrix = buildExifAndScaleMatrix(
            orientation = orientation,
            sampledWidth = sampledBitmap.width,
            sampledHeight = sampledBitmap.height,
            targetUprightWidth = targetW,
            targetUprightHeight = targetH
        )

        if (matrix.isIdentity) {
            return sampledBitmap
        }

        val transformed = Bitmap.createBitmap(
            sampledBitmap,
            0,
            0,
            sampledBitmap.width,
            sampledBitmap.height,
            matrix,
            true
        )
        if (transformed !== sampledBitmap) {
            sampledBitmap.recycle()
        }
        return transformed
    }

    private fun computeHeapSafeTargetDimensions(
        naturalWidth: Int,
        naturalHeight: Int,
        maxSideLimit: Int
    ): Pair<Int, Int> {
        val maxNaturalSide = max(naturalWidth, naturalHeight)
        var scale = if (maxNaturalSide > maxSideLimit) {
            maxSideLimit.toDouble() / maxNaturalSide.toDouble()
        } else {
            1.0
        }

        var targetW = (naturalWidth * scale).roundToInt().coerceAtLeast(1)
        var targetH = (naturalHeight * scale).roundToInt().coerceAtLeast(1)

        val budgetBytes = availableHeapBudgetBytes()
        while (estimatedRgbaBytes(targetW, targetH) > budgetBytes && max(targetW, targetH) > 512) {
            scale *= 0.75
            targetW = (naturalWidth * scale).roundToInt().coerceAtLeast(1)
            targetH = (naturalHeight * scale).roundToInt().coerceAtLeast(1)
        }
        return targetW to targetH
    }

    private fun availableHeapBudgetBytes(): Long {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val remaining = (runtime.maxMemory() - used).coerceAtLeast(16L * 1024L * 1024L)
        return (remaining * 0.45).toLong()
    }

    private fun estimatedRgbaBytes(width: Int, height: Int): Long =
        width.toLong().coerceAtLeast(1L) * height.toLong().coerceAtLeast(1L) * 4L

    private fun buildExifAndScaleMatrix(
        orientation: Int,
        sampledWidth: Int,
        sampledHeight: Int,
        targetUprightWidth: Int,
        targetUprightHeight: Int
    ): Matrix {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        val swapped = isOrientationAxisSwapped(orientation)
        val orientedSampleW = if (swapped) sampledHeight else sampledWidth
        val orientedSampleH = if (swapped) sampledWidth else sampledHeight
        if (orientedSampleW > targetUprightWidth || orientedSampleH > targetUprightHeight) {
            val sx = targetUprightWidth.toFloat() / orientedSampleW.toFloat()
            val sy = targetUprightHeight.toFloat() / orientedSampleH.toFloat()
            val s = min(sx, sy)
            matrix.postScale(s, s)
        }
        return matrix
    }

    private fun readOrientedDimensions(file: File): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        if (w <= 0 || h <= 0) return 0 to 0
        val orientation = readExifOrientation(file)
        return if (isOrientationAxisSwapped(orientation)) h to w else w to h
    }

    private fun readExifOrientation(file: File): Int {
        return runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun isOrientationAxisSwapped(orientation: Int): Boolean =
        orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE

    private fun resolveCompressFormat(format: ImagePayloadFormat): Bitmap.CompressFormat {
        return when (format) {
            ImagePayloadFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImagePayloadFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }
    }

    private fun detectMimeType(file: File): String? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return opts.outMimeType?.lowercase()
    }

    private fun guessMimeFromUri(uri: Uri): String? {
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
        if (ext.isBlank()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.lowercase()
    }

    private fun extensionForMime(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        else -> "jpg"
    }

    private fun queryUriSizeBytes(uri: Uri): Long? {
        return runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                val length = afd.length
                if (length >= 0L) length else null
            }
        }.getOrNull()
    }

    private class FileSizeLimitExceededException : IOException()

    companion object {
        const val MAX_STAGING_FILE_BYTES = 64L * 1024L * 1024L
        const val THUMBNAIL_MAX_SIDE_PX = 400
        const val THUMBNAIL_WEBP_QUALITY = 80
        const val PAYLOAD_WEBP_QUALITY = 85
        const val PAYLOAD_JPEG_QUALITY = 90
        const val MIN_PAYLOAD_SIDE_PX = 256

        private val ALLOWED_MIME_TYPES = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/heic",
            "image/heif"
        )
    }
}
