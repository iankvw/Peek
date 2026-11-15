package com.iank.im.peek.domain.usecase

import android.net.Uri
import com.iank.im.peek.data.local.image.ImagePreprocessor
import com.iank.im.peek.data.local.image.InternalImageStorage
import com.iank.im.peek.data.local.image.StageImageResult
import com.iank.im.peek.domain.model.PendingAttachment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StageAttachmentUseCase @Inject constructor(
    private val imagePreprocessor: ImagePreprocessor,
    private val imageStorage: InternalImageStorage
) {
    suspend fun stageFromCameraFile(
        stagingOriginalRelPath: String,
        id: String
    ): StageImageResult = imagePreprocessor.stageFromCameraFile(stagingOriginalRelPath, id)

    suspend fun stageFromGalleryUri(uri: Uri): StageImageResult =
        imagePreprocessor.stageFromGalleryUri(uri)

    fun removePendingAttachment(pending: PendingAttachment) {
        imageStorage.deleteRelPath(pending.stagingOriginalRelPath)
        imageStorage.deleteRelPath(pending.stagingThumbnailRelPath)
    }

    fun removePendingList(list: List<PendingAttachment>) {
        list.forEach { removePendingAttachment(it) }
    }
}
