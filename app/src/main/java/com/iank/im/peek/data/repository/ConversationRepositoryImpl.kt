package com.iank.im.peek.data.repository

import androidx.room.withTransaction
import com.iank.im.peek.data.local.db.PeekDatabase
import com.iank.im.peek.data.local.db.dao.ConversationDao
import com.iank.im.peek.data.local.db.dao.FileDeletionQueueDao
import com.iank.im.peek.data.local.db.dao.MessageDao
import com.iank.im.peek.data.local.db.dao.RemoteFileCacheDao
import com.iank.im.peek.data.local.db.entity.ConversationEntity
import com.iank.im.peek.data.local.db.entity.MessageAttachmentEntity
import com.iank.im.peek.data.local.db.entity.MessageEntity
import com.iank.im.peek.data.local.db.entity.MessageWithAttachments
import com.iank.im.peek.data.local.db.entity.PendingFileDeletionEntity
import com.iank.im.peek.data.local.db.entity.PendingRemoteDeletionEntity
import com.iank.im.peek.data.local.image.InternalImageStorage
import com.iank.im.peek.domain.model.ApiError
import com.iank.im.peek.domain.model.ChatMessage
import com.iank.im.peek.domain.model.CommittedAttachmentInput
import com.iank.im.peek.domain.model.Conversation
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.model.MessageAttachment
import com.iank.im.peek.domain.model.MessageRole
import com.iank.im.peek.domain.model.MessageStatus
import com.iank.im.peek.domain.repository.ConversationRepository
import com.iank.im.peek.domain.repository.PagedConversations
import com.iank.im.peek.domain.repository.PagedMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val database: PeekDatabase,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val remoteFileCacheDao: RemoteFileCacheDao,
    private val fileDeletionQueueDao: FileDeletionQueueDao,
    private val imageStorage: InternalImageStorage
) : ConversationRepository {

    override fun observeConversations(query: String): Flow<List<Conversation>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            conversationDao.observeAll().map { list -> list.map { it.toDomain() } }
        } else {
            val escapedQuery = escapeSqlLikeQuery(trimmed)
            conversationDao.observeByQuery(escapedQuery).map { rows ->
                rows.map { row ->
                    val titleMatches = row.conversation.title.contains(trimmed, ignoreCase = true)
                    val rawMatch = row.matchedMessageText ?: row.matchedCaptionText
                    val snippet = if (!titleMatches && !rawMatch.isNullOrBlank()) {
                        extractMatchSnippet(rawMatch, trimmed)
                    } else {
                        null
                    }
                    row.conversation.toDomain(matchedSnippet = snippet)
                }
            }
        }
    }

    private fun escapeSqlLikeQuery(query: String): String = query
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private fun extractMatchSnippet(sourceText: String, query: String): String {
        val normalized = sourceText
            .replace(Regex("[*_`~#>\\[\\]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isEmpty()) return ""
        val idx = normalized.indexOf(query, ignoreCase = true)
        if (idx < 0) return normalized.take(60)
        val start = (idx - 16).coerceAtLeast(0)
        val end = (idx + query.length + 36).coerceAtMost(normalized.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < normalized.length) "…" else ""
        return "$prefix${normalized.substring(start, end).trim()}$suffix"
    }

    override fun observeConversation(conversationId: String): Flow<Conversation?> {
        return conversationDao.observeById(conversationId).map { it?.toDomain() }
    }

    override suspend fun getConversation(conversationId: String): Conversation? {
        return conversationDao.getById(conversationId)?.toDomain()
    }

    override suspend fun getConversationsPage(
        beforeUpdatedAt: Long?,
        beforeId: String?,
        pageSize: Int
    ): PagedConversations {
        val safeSize = pageSize.coerceIn(1, 30)
        val rows = conversationDao.getConversationsPage(
            beforeUpdatedAt = beforeUpdatedAt,
            beforeId = beforeId,
            limitPlusOne = safeSize + 1
        )
        val hasMore = rows.size > safeSize
        val sliced = rows.take(safeSize).map { it.toDomain() }
        val last = sliced.lastOrNull()
        return PagedConversations(
            items = sliced,
            hasMore = hasMore,
            nextCursorUpdatedAt = if (hasMore) last?.updatedAt else null,
            nextCursorId = if (hasMore) last?.id else null
        )
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> {
        return messageDao.observeMessagesWithAttachments(conversationId)
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getMessages(conversationId: String): List<ChatMessage> {
        return messageDao.getMessagesWithAttachments(conversationId).map { it.toDomain() }
    }

    override suspend fun getMessagesPage(
        conversationId: String,
        beforePosition: Int?,
        pageSize: Int
    ): PagedMessages {
        val safeSize = pageSize.coerceIn(1, 30)
        val conversation = conversationDao.getById(conversationId)?.toDomain()
        val rowsDesc = messageDao.getMessagesPageWithAttachments(
            conversationId = conversationId,
            beforePosition = beforePosition,
            limitPlusOne = safeSize + 1
        )
        val hasOlder = rowsDesc.size > safeSize
        val itemsAscending = rowsDesc
            .take(safeSize)
            .asReversed()
            .map { it.toDomain() }
        val oldestPosition = itemsAscending.firstOrNull()?.position
        return PagedMessages(
            conversation = conversation,
            itemsAscending = itemsAscending,
            hasOlder = hasOlder,
            nextBeforePosition = if (hasOlder) oldestPosition else null
        )
    }

    override suspend fun getMessage(messageId: String): ChatMessage? {
        return messageDao.getMessageWithAttachments(messageId)?.toDomain()
    }

    override suspend fun getAttachment(attachmentId: String): MessageAttachment? {
        return messageDao.getAttachmentById(attachmentId)?.toDomain()
    }

    override suspend fun commitTurn(
        conversationId: String?,
        conversationTitle: String,
        activeTargetId: String?,
        userMessageId: String,
        assistantMessageId: String,
        userText: String,
        committedAttachments: List<CommittedAttachmentInput>,
        presetId: String,
        modelId: String
    ): String {
        val now = System.currentTimeMillis()
        val resolvedConversationId = conversationId ?: UUID.randomUUID().toString()

        database.withTransaction {
            val existing = conversationDao.getById(resolvedConversationId)
            if (existing == null) {
                conversationDao.upsert(
                    ConversationEntity(
                        id = resolvedConversationId,
                        title = conversationTitle,
                        activeTargetId = activeTargetId,
                        overrideResolutionPreset = null,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            } else {
                if (activeTargetId != null && existing.activeTargetId != activeTargetId) {
                    conversationDao.updateActiveTargetId(resolvedConversationId, activeTargetId)
                }
                conversationDao.updateUpdatedAt(resolvedConversationId, now)
            }

            val nextPos = messageDao.getMaxPosition(resolvedConversationId) + 1
            val nextTurn = messageDao.getMaxTurnIndex(resolvedConversationId) + 1

            messageDao.insertMessage(
                MessageEntity(
                    id = userMessageId,
                    conversationId = resolvedConversationId,
                    position = nextPos,
                    turnIndex = nextTurn,
                    role = MessageRole.USER,
                    textContent = userText,
                    status = MessageStatus.SUCCESS,
                    errorCode = null,
                    errorMessage = null,
                    presetIdUsed = presetId,
                    modelIdUsed = modelId,
                    providerMetadataJson = null,
                    createdAt = now
                )
            )

            if (committedAttachments.isNotEmpty()) {
                val attachmentEntities = committedAttachments.mapIndexed { index, item ->
                    MessageAttachmentEntity(
                        id = item.id,
                        messageId = userMessageId,
                        sortIndex = index,
                        originalRelPath = item.originalRelPath,
                        originalMimeType = item.originalMimeType,
                        originalWidth = item.originalWidth,
                        originalHeight = item.originalHeight,
                        thumbnailRelPath = item.thumbnailRelPath,
                        payloadRelPath = null,
                        payloadMaxSide = null,
                        payloadRevision = 0,
                        requestedPreset = ImageResolutionPreset.DEFAULT,
                        isTextReplaced = false,
                        imageCaption = null
                    )
                }
                messageDao.insertAttachments(attachmentEntities)
            }

            messageDao.insertMessage(
                MessageEntity(
                    id = assistantMessageId,
                    conversationId = resolvedConversationId,
                    position = nextPos + 1,
                    turnIndex = nextTurn,
                    role = MessageRole.ASSISTANT,
                    textContent = "",
                    status = MessageStatus.LOADING,
                    errorCode = null,
                    errorMessage = null,
                    presetIdUsed = presetId,
                    modelIdUsed = modelId,
                    providerMetadataJson = null,
                    createdAt = now + 1L
                )
            )
        }
        return resolvedConversationId
    }

    override suspend fun prepareRetryAssistantMessage(
        assistantMessageId: String,
        presetId: String,
        modelId: String
    ): ChatMessage? {
        return database.withTransaction {
            val assistantMsg = messageDao.getMessageById(assistantMessageId) ?: return@withTransaction null
            if (assistantMsg.role != MessageRole.ASSISTANT) return@withTransaction null
            messageDao.resetAssistantForRetry(
                messageId = assistantMessageId,
                status = MessageStatus.LOADING,
                presetIdUsed = presetId,
                modelIdUsed = modelId
            )
            messageDao.getMessageInTurn(
                conversationId = assistantMsg.conversationId,
                turnIndex = assistantMsg.turnIndex,
                role = MessageRole.USER
            )?.toDomain()
        }
    }

    override suspend fun updateAssistantSuccess(
        messageId: String,
        textContent: String,
        presetId: String,
        modelId: String,
        servedGroupName: String?,
        servedPresetName: String?,
        servedModelName: String?,
        providerMetadataJson: String?
    ) {
        database.withTransaction {
            val message = messageDao.getMessageById(messageId) ?: return@withTransaction
            messageDao.updateAssistantResult(
                messageId = messageId,
                status = MessageStatus.SUCCESS,
                errorCode = null,
                errorMessage = null,
                textContent = textContent,
                presetIdUsed = presetId,
                modelIdUsed = modelId,
                servedGroupName = servedGroupName,
                servedPresetName = servedPresetName,
                servedModelName = servedModelName,
                providerMetadataJson = providerMetadataJson
            )
            conversationDao.updateUpdatedAt(message.conversationId, System.currentTimeMillis())
        }
    }

    override suspend fun updateAssistantError(messageId: String, error: ApiError) {
        messageDao.updateMessageStatus(
            messageId = messageId,
            status = MessageStatus.ERROR,
            errorCode = error.code,
            errorMessage = error.detailMessage
        )
    }

    override suspend fun updateAssistantCancelled(messageId: String) {
        messageDao.updateMessageStatus(
            messageId = messageId,
            status = MessageStatus.CANCELLED,
            errorCode = null,
            errorMessage = null
        )
    }

    override suspend fun updateMessageText(messageId: String, newText: String) {
        database.withTransaction {
            val message = messageDao.getMessageById(messageId) ?: return@withTransaction
            messageDao.updateMessageText(messageId, newText)
            if (message.role == MessageRole.ASSISTANT && message.providerMetadataJson != null) {
                messageDao.updateAssistantResult(
                    messageId = messageId,
                    status = message.status,
                    errorCode = message.errorCode,
                    errorMessage = message.errorMessage,
                    textContent = newText,
                    presetIdUsed = message.presetIdUsed,
                    modelIdUsed = message.modelIdUsed,
                    servedGroupName = message.servedGroupName,
                    servedPresetName = message.servedPresetName,
                    servedModelName = message.servedModelName,
                    providerMetadataJson = null
                )
            }
            conversationDao.updateUpdatedAt(message.conversationId, System.currentTimeMillis())
        }
    }

    override suspend fun markMessageReported(messageId: String, isReported: Boolean) {
        messageDao.updateMessageReported(messageId, isReported)
    }

    override suspend fun truncateAfterAndReplaceUserMessage(
        messageId: String,
        newText: String,
        assistantMessageId: String,
        presetId: String,
        modelId: String
    ): String? {
        val now = System.currentTimeMillis()
        val conversationId = database.withTransaction {
            val target = messageDao.getMessageById(messageId) ?: return@withTransaction null
            if (target.role != MessageRole.USER) return@withTransaction null

            val attachmentsToDelete = messageDao.getAttachmentsAfterPosition(
                conversationId = target.conversationId,
                afterPosition = target.position
            )
            enqueueAttachmentDeletionsLocked(attachmentsToDelete, now)

            messageDao.deleteMessagesAfterPosition(
                conversationId = target.conversationId,
                afterPosition = target.position
            )
            messageDao.updateMessageText(target.id, newText)

            messageDao.insertMessage(
                MessageEntity(
                    id = assistantMessageId,
                    conversationId = target.conversationId,
                    position = target.position + 1,
                    turnIndex = target.turnIndex,
                    role = MessageRole.ASSISTANT,
                    textContent = "",
                    status = MessageStatus.LOADING,
                    errorCode = null,
                    errorMessage = null,
                    presetIdUsed = presetId,
                    modelIdUsed = modelId,
                    providerMetadataJson = null,
                    createdAt = now
                )
            )
            conversationDao.updateUpdatedAt(target.conversationId, now)
            target.conversationId
        }

        processPendingFileDeletions()
        return conversationId
    }

    override suspend fun updateAttachmentPayload(
        attachmentId: String,
        newPayloadRelPath: String,
        newPayloadMaxSide: Int,
        expectedPreviousRevision: Int
    ): Boolean {
        val now = System.currentTimeMillis()
        val updated = database.withTransaction {
            val current = messageDao.getAttachmentById(attachmentId) ?: return@withTransaction false
            if (current.payloadRevision != expectedPreviousRevision) return@withTransaction false

            val oldPayload = current.payloadRelPath
            val rows = messageDao.updateAttachmentPayload(
                attachmentId = attachmentId,
                payloadRelPath = newPayloadRelPath,
                payloadMaxSide = newPayloadMaxSide,
                expectedRevision = expectedPreviousRevision
            )
            if (rows == 0) return@withTransaction false

            if (!oldPayload.isNullOrBlank() && oldPayload != newPayloadRelPath) {
                fileDeletionQueueDao.enqueueLocalDeletions(
                    listOf(PendingFileDeletionEntity(relPath = oldPayload, enqueuedAt = now))
                )
            }
            enqueueRemoteCacheDeletionsForAttachmentsLocked(listOf(attachmentId), now)
            true
        }
        if (updated) {
            processPendingFileDeletions()
        }
        return updated
    }

    override suspend fun replaceAttachment(
        attachmentId: String,
        newOriginalRelPath: String,
        newMimeType: String,
        newWidth: Int,
        newHeight: Int,
        newThumbnailRelPath: String
    ): Boolean {
        val now = System.currentTimeMillis()
        val updated = database.withTransaction {
            val old = messageDao.getAttachmentById(attachmentId) ?: return@withTransaction false
            val oldPaths = listOfNotNull(
                old.originalRelPath.takeIf { it != newOriginalRelPath },
                old.thumbnailRelPath.takeIf { it != newThumbnailRelPath },
                old.payloadRelPath
            ).map { PendingFileDeletionEntity(relPath = it, enqueuedAt = now) }

            if (oldPaths.isNotEmpty()) {
                fileDeletionQueueDao.enqueueLocalDeletions(oldPaths)
            }
            enqueueRemoteCacheDeletionsForAttachmentsLocked(listOf(attachmentId), now)

            messageDao.updateAttachmentReplacement(
                attachmentId = attachmentId,
                originalRelPath = newOriginalRelPath,
                originalMimeType = newMimeType,
                originalWidth = newWidth,
                originalHeight = newHeight,
                thumbnailRelPath = newThumbnailRelPath
            ) > 0
        }
        if (updated) {
            processPendingFileDeletions()
        }
        return updated
    }

    override suspend fun toggleAttachmentTextReplaced(attachmentId: String) {
        database.withTransaction {
            val current = messageDao.getAttachmentById(attachmentId) ?: return@withTransaction
            messageDao.updateAttachmentTextReplaced(attachmentId, !current.isTextReplaced)
        }
    }

    override suspend fun updateAttachmentCaption(attachmentId: String, caption: String) {
        messageDao.updateAttachmentCaption(attachmentId, caption)
    }

    override suspend fun updateConversationTitle(conversationId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isNotEmpty()) {
            conversationDao.updateTitle(conversationId, trimmed)
        }
    }

    override suspend fun updateConversationActiveTarget(conversationId: String, targetId: String?) {
        conversationDao.updateActiveTargetId(conversationId, targetId)
    }

    override suspend fun updateSingleAttachmentResolution(
        attachmentId: String,
        preset: ImageResolutionPreset
    ) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            messageDao.updateAttachmentRequestedPreset(attachmentId, preset)
            enqueueRemoteCacheDeletionsForAttachmentsLocked(listOf(attachmentId), now)
        }
    }

    override suspend fun updateConversationResolution(
        conversationId: String,
        preset: ImageResolutionPreset
    ) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val effectiveOverride = if (preset == ImageResolutionPreset.DEFAULT) null else preset
            conversationDao.updateOverrideResolution(conversationId, effectiveOverride, now)
            messageDao.resetRequestedPresetForConversation(conversationId, ImageResolutionPreset.DEFAULT)
            val attachmentIds = messageDao.getAttachmentsForConversation(conversationId).map { it.id }
            if (attachmentIds.isNotEmpty()) {
                enqueueRemoteCacheDeletionsForAttachmentsLocked(attachmentIds, now)
            }
        }
    }

    override suspend fun deleteConversation(conversationId: String) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val attachments = messageDao.getAttachmentsForConversation(conversationId)
            enqueueAttachmentDeletionsLocked(attachments, now)
            conversationDao.deleteById(conversationId)
        }
        processPendingFileDeletions()
    }

    override suspend fun processPendingFileDeletions() = withContext(Dispatchers.IO) {
        val pending = fileDeletionQueueDao.getPendingLocalDeletions()
        if (pending.isEmpty()) return@withContext
        val deletedPaths = mutableListOf<String>()
        for (entry in pending) {
            if (imageStorage.deleteRelPath(entry.relPath)) {
                deletedPaths += entry.relPath
            }
        }
        if (deletedPaths.isNotEmpty()) {
            deletedPaths.chunked(500).forEach { chunk ->
                fileDeletionQueueDao.removeLocalDeletions(chunk)
            }
        }
    }

    private suspend fun enqueueAttachmentDeletionsLocked(
        attachments: List<MessageAttachmentEntity>,
        now: Long
    ) {
        if (attachments.isEmpty()) return
        val localEntries = attachments.flatMap { att ->
            listOfNotNull(att.originalRelPath, att.thumbnailRelPath, att.payloadRelPath)
        }.distinct().map { relPath ->
            PendingFileDeletionEntity(relPath = relPath, enqueuedAt = now)
        }
        if (localEntries.isNotEmpty()) {
            fileDeletionQueueDao.enqueueLocalDeletions(localEntries)
        }
        enqueueRemoteCacheDeletionsForAttachmentsLocked(attachments.map { it.id }, now)
    }

    private suspend fun enqueueRemoteCacheDeletionsForAttachmentsLocked(
        attachmentIds: List<String>,
        now: Long
    ) {
        if (attachmentIds.isEmpty()) return
        val remoteEntries = attachmentIds.chunked(500).flatMap { chunk ->
            remoteFileCacheDao.getAllForAttachments(chunk)
        }
        if (remoteEntries.isNotEmpty()) {
            val pendingRemote = remoteEntries.map { cached ->
                PendingRemoteDeletionEntity(
                    fileName = cached.fileName,
                    credentialFingerprint = cached.credentialFingerprint,
                    enqueuedAt = now
                )
            }
            fileDeletionQueueDao.enqueueRemoteDeletions(pendingRemote)
            attachmentIds.chunked(500).forEach { chunk ->
                remoteFileCacheDao.deleteByAttachmentIds(chunk)
            }
        }
    }

    private fun ConversationEntity.toDomain(matchedSnippet: String? = null): Conversation = Conversation(
        id = id,
        title = title,
        activeTargetId = activeTargetId,
        overrideResolutionPreset = overrideResolutionPreset,
        createdAt = createdAt,
        updatedAt = updatedAt,
        matchedSnippet = matchedSnippet
    )

    private fun MessageWithAttachments.toDomain(): ChatMessage = ChatMessage(
        id = message.id,
        conversationId = message.conversationId,
        position = message.position,
        turnIndex = message.turnIndex,
        role = message.role,
        textContent = message.textContent,
        status = message.status,
        errorCode = message.errorCode,
        errorMessage = message.errorMessage,
        presetIdUsed = message.presetIdUsed,
        modelIdUsed = message.modelIdUsed,
        servedGroupName = message.servedGroupName,
        servedPresetName = message.servedPresetName,
        servedModelName = message.servedModelName,
        providerMetadataJson = message.providerMetadataJson,
        createdAt = message.createdAt,
        attachments = attachments.sortedBy { it.sortIndex }.map { it.toDomain() },
        isReported = message.isReported
    )

    private fun MessageAttachmentEntity.toDomain(): MessageAttachment = MessageAttachment(
        id = id,
        messageId = messageId,
        sortIndex = sortIndex,
        originalRelPath = originalRelPath,
        originalMimeType = originalMimeType,
        originalWidth = originalWidth,
        originalHeight = originalHeight,
        thumbnailRelPath = thumbnailRelPath,
        payloadRelPath = payloadRelPath,
        payloadMaxSide = payloadMaxSide,
        payloadRevision = payloadRevision,
        requestedPreset = requestedPreset,
        isTextReplaced = isTextReplaced,
        imageCaption = imageCaption
    )
}
