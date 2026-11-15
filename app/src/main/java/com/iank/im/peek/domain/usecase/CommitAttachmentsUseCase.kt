package com.iank.im.peek.domain.usecase

import com.iank.im.peek.data.local.image.InternalImageStorage
import com.iank.im.peek.domain.model.CommittedAttachmentInput
import com.iank.im.peek.domain.model.PendingAttachment
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommitAttachmentsUseCase @Inject constructor(
    private val imageStorage: InternalImageStorage
) {
    fun commitStaged(pendingList: List<PendingAttachment>): List<CommittedAttachmentInput> {
        imageStorage.ensureDirectories()
        val committed = mutableListOf<CommittedAttachmentInput>()
        for (item in pendingList) {
            val ext = item.stagingOriginalRelPath.substringAfterLast('.', "jpg")
            val targetOriginalRelPath = imageStorage.createOriginalRelPath(item.id, ext)
            val targetThumbRelPath = imageStorage.createThumbRelPath(item.id)

            val movedOriginal = imageStorage.moveAtomically(
                sourceRelPath = item.stagingOriginalRelPath,
                targetRelPath = targetOriginalRelPath
            )
            if (!movedOriginal) {
                throw IOException("Failed to commit staged original image: ${item.stagingOriginalRelPath}")
            }

            val movedThumb = imageStorage.moveAtomically(
                sourceRelPath = item.stagingThumbnailRelPath,
                targetRelPath = targetThumbRelPath
            )
            if (!movedThumb) {
                throw IOException("Failed to commit staged thumbnail: ${item.stagingThumbnailRelPath}")
            }

            committed += CommittedAttachmentInput(
                id = item.id,
                originalRelPath = targetOriginalRelPath,
                originalMimeType = item.mimeType,
                originalWidth = item.width,
                originalHeight = item.height,
                thumbnailRelPath = targetThumbRelPath
            )
        }
        return committed
    }
}
