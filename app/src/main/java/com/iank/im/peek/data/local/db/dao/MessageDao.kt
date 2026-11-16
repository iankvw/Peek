package com.iank.im.peek.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.iank.im.peek.data.local.db.entity.MessageAttachmentEntity
import com.iank.im.peek.data.local.db.entity.MessageEntity
import com.iank.im.peek.data.local.db.entity.MessageWithAttachments
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.model.MessageRole
import com.iank.im.peek.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

data class AttachmentPathsTuple(
    val originalRelPath: String,
    val thumbnailRelPath: String,
    val payloadRelPath: String?
)

@Dao
interface MessageDao {
    @Transaction
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY position ASC")
    fun observeMessagesWithAttachments(conversationId: String): Flow<List<MessageWithAttachments>>

    @Transaction
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY position ASC")
    suspend fun getMessagesWithAttachments(conversationId: String): List<MessageWithAttachments>

    @Transaction
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageWithAttachments(messageId: String): MessageWithAttachments?

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Transaction
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND turnIndex = :turnIndex AND role = :role LIMIT 1")
    suspend fun getMessageInTurn(conversationId: String, turnIndex: Int, role: MessageRole): MessageWithAttachments?

    @Query("SELECT COALESCE(MAX(position), -1) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMaxPosition(conversationId: String): Int

    @Query("SELECT COALESCE(MAX(turnIndex), -1) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMaxTurnIndex(conversationId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<MessageAttachmentEntity>)

    @Query(
        """
        UPDATE messages
        SET status = :status,
            errorCode = :errorCode,
            errorMessage = :errorMessage,
            textContent = :textContent,
            presetIdUsed = :presetIdUsed,
            modelIdUsed = :modelIdUsed,
            servedGroupName = :servedGroupName,
            servedPresetName = :servedPresetName,
            servedModelName = :servedModelName,
            providerMetadataJson = :providerMetadataJson
        WHERE id = :messageId
        """
    )
    suspend fun updateAssistantResult(
        messageId: String,
        status: MessageStatus,
        errorCode: String?,
        errorMessage: String?,
        textContent: String,
        presetIdUsed: String?,
        modelIdUsed: String?,
        servedGroupName: String?,
        servedPresetName: String?,
        servedModelName: String?,
        providerMetadataJson: String?
    )

    @Query("UPDATE messages SET status = :status, errorCode = :errorCode, errorMessage = :errorMessage WHERE id = :messageId")
    suspend fun updateMessageStatus(
        messageId: String,
        status: MessageStatus,
        errorCode: String?,
        errorMessage: String?
    )

    @Query(
        """
        UPDATE messages
        SET status = :status,
            errorCode = NULL,
            errorMessage = NULL,
            presetIdUsed = :presetIdUsed,
            modelIdUsed = :modelIdUsed,
            servedGroupName = NULL,
            servedPresetName = NULL,
            servedModelName = NULL
        WHERE id = :messageId
        """
    )
    suspend fun resetAssistantForRetry(
        messageId: String,
        status: MessageStatus,
        presetIdUsed: String?,
        modelIdUsed: String?
    )

    @Query("UPDATE messages SET textContent = :newText WHERE id = :messageId")
    suspend fun updateMessageText(messageId: String, newText: String)

    @Query("SELECT id FROM messages WHERE status = 'LOADING'")
    suspend fun getLoadingMessageIds(): List<String>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND position > :afterPosition")
    suspend fun deleteMessagesAfterPosition(conversationId: String, afterPosition: Int)

    @Query("SELECT * FROM message_attachments WHERE id = :attachmentId")
    suspend fun getAttachmentById(attachmentId: String): MessageAttachmentEntity?

    @Query(
        """
        SELECT ma.* FROM message_attachments ma
        INNER JOIN messages m ON ma.messageId = m.id
        WHERE m.conversationId = :conversationId
        """
    )
    suspend fun getAttachmentsForConversation(conversationId: String): List<MessageAttachmentEntity>

    @Query(
        """
        SELECT ma.* FROM message_attachments ma
        INNER JOIN messages m ON ma.messageId = m.id
        WHERE m.conversationId = :conversationId AND m.position > :afterPosition
        """
    )
    suspend fun getAttachmentsAfterPosition(
        conversationId: String,
        afterPosition: Int
    ): List<MessageAttachmentEntity>

    @Query(
        """
        UPDATE message_attachments
        SET payloadRelPath = :payloadRelPath,
            payloadMaxSide = :payloadMaxSide,
            payloadRevision = payloadRevision + 1
        WHERE id = :attachmentId AND payloadRevision = :expectedRevision
        """
    )
    suspend fun updateAttachmentPayload(
        attachmentId: String,
        payloadRelPath: String,
        payloadMaxSide: Int,
        expectedRevision: Int
    ): Int

    @Query(
        """
        UPDATE message_attachments
        SET originalRelPath = :originalRelPath,
            originalMimeType = :originalMimeType,
            originalWidth = :originalWidth,
            originalHeight = :originalHeight,
            thumbnailRelPath = :thumbnailRelPath,
            payloadRelPath = NULL,
            payloadMaxSide = NULL,
            payloadRevision = payloadRevision + 1,
            imageCaption = NULL
        WHERE id = :attachmentId
        """
    )
    suspend fun updateAttachmentReplacement(
        attachmentId: String,
        originalRelPath: String,
        originalMimeType: String,
        originalWidth: Int,
        originalHeight: Int,
        thumbnailRelPath: String
    ): Int

    @Query("UPDATE message_attachments SET isTextReplaced = :isTextReplaced WHERE id = :attachmentId")
    suspend fun updateAttachmentTextReplaced(attachmentId: String, isTextReplaced: Boolean)

    @Query("UPDATE message_attachments SET imageCaption = :caption WHERE id = :attachmentId")
    suspend fun updateAttachmentCaption(attachmentId: String, caption: String)

    @Query("UPDATE message_attachments SET requestedPreset = :preset WHERE id = :attachmentId")
    suspend fun updateAttachmentRequestedPreset(attachmentId: String, preset: ImageResolutionPreset)

    @Query(
        """
        UPDATE message_attachments
        SET requestedPreset = :preset
        WHERE messageId IN (SELECT id FROM messages WHERE conversationId = :conversationId)
        """
    )
    suspend fun resetRequestedPresetForConversation(conversationId: String, preset: ImageResolutionPreset)

    @Query("SELECT originalRelPath, thumbnailRelPath, payloadRelPath FROM message_attachments")
    suspend fun getAllReferencedRelPaths(): List<AttachmentPathsTuple>
}
