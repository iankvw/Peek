package com.iank.im.peek.wear.data.bridge

import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.NodeClient
import com.iank.im.peek.wear.data.cache.WearImageCache
import com.iank.im.peek.wear.di.ApplicationScope
import com.iank.im.peek.wear.domain.model.WearAttachmentItem
import com.iank.im.peek.wear.domain.model.WearChatUpdate
import com.iank.im.peek.wear.domain.model.WearConversationItem
import com.iank.im.peek.wear.domain.model.WearConversationsPage
import com.iank.im.peek.wear.domain.model.WearImageQuality
import com.iank.im.peek.wear.domain.model.WearMessageItem
import com.iank.im.peek.wear.domain.model.WearMessageRole
import com.iank.im.peek.wear.domain.model.WearMessageStatus
import com.iank.im.peek.wear.domain.model.WearMessagesPage
import com.iank.im.peek.wear.domain.model.WearSendTurnResult
import com.iank.im.peek.wear.domain.model.WearSimpleResult
import com.iank.im.peek.wear.domain.model.WearSyncedConfig
import com.iank.im.peek.wear.domain.model.WearTargetItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearPhoneBridgeClient @Inject constructor(
    private val messageClient: MessageClient,
    private val dataClient: DataClient,
    private val channelClient: ChannelClient,
    private val nodeClient: NodeClient,
    private val imageCache: WearImageCache,
    private val json: Json,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {
    private val started = AtomicBoolean(false)

    // Concurrency control: limit automatic thumbnail streams to 2 at a time to protect BT bandwidth.
    // User-triggered full-screen image requests bypass this semaphore for priority delivery.
    private val thumbnailSemaphore = Semaphore(MAX_CONCURRENT_THUMBNAILS)
    private val inFlightThumbnails = ConcurrentHashMap<String, Deferred<File?>>()
    private val inFlightFullImages = ConcurrentHashMap<String, Deferred<File?>>()
    private val deferredWaiters = ConcurrentHashMap<Deferred<File?>, AtomicInteger>()

    private class PendingChannelTransfer(
        val cacheKey: String,
        val deferred: CompletableDeferred<File?>
    ) {
        @Volatile
        var channel: ChannelClient.Channel? = null

        @Volatile
        var streamJob: Job? = null
    }

    private val pendingChannelTransfers = ConcurrentHashMap<String, PendingChannelTransfer>()

    private val _syncedConfig = MutableStateFlow(WearSyncedConfig())
    val syncedConfig: StateFlow<WearSyncedConfig> = _syncedConfig.asStateFlow()

    private val _isPhoneConnected = MutableStateFlow(false)
    val isPhoneConnected: StateFlow<Boolean> = _isPhoneConnected.asStateFlow()

    private val _chatUpdates = MutableSharedFlow<WearChatUpdate>(extraBufferCapacity = 32)
    val chatUpdates: SharedFlow<WearChatUpdate> = _chatUpdates.asSharedFlow()

    private val dataListener = DataClient.OnDataChangedListener { events ->
        val configPayloads = mutableListOf<String>()
        try {
            for (event in events) {
                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == WearBridgeConstants.PATH_STATE_CONFIG
                ) {
                    val raw = DataMapItem.fromDataItem(event.dataItem)
                        .dataMap
                        .getString(WearBridgeConstants.KEY_CONFIG_JSON)
                    if (!raw.isNullOrBlank()) {
                        configPayloads += raw
                    }
                }
            }
        } finally {
            events.release()
        }
        configPayloads.forEach { applySyncedConfigJson(it) }
    }

    private val messageListener = MessageClient.OnMessageReceivedListener { messageEvent ->
        handleIncomingMessage(messageEvent)
    }

    private val channelCallback = object : ChannelClient.ChannelCallback() {
        override fun onChannelOpened(channel: ChannelClient.Channel) {
            handleIncomingChannel(channel)
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        dataClient.addListener(dataListener)
        messageClient.addListener(messageListener)
        channelClient.registerChannelCallback(channelCallback)
        applicationScope.launch {
            refreshInitialSyncedConfig()
            checkConnection()
        }
    }

    suspend fun refreshInitialSyncedConfig() {
        runCatching {
            val items = dataClient.getDataItems(
                Uri.parse("wear://*${WearBridgeConstants.PATH_STATE_CONFIG}")
            ).await()
            try {
                var latestJson: String? = null
                for (item in items) {
                    if (item.uri.path == WearBridgeConstants.PATH_STATE_CONFIG) {
                        val raw = DataMapItem.fromDataItem(item)
                            .dataMap
                            .getString(WearBridgeConstants.KEY_CONFIG_JSON)
                        if (!raw.isNullOrBlank()) {
                            latestJson = raw
                        }
                    }
                }
                latestJson?.let { applySyncedConfigJson(it) }
            } finally {
                items.release()
            }
        }.onFailure { t ->
            Log.w(TAG, "Failed loading initial Wear DataClient config", t)
        }
    }

    suspend fun checkConnection(): Boolean {
        val nodeId = resolvePhoneNodeId()
        val connected = nodeId != null
        _isPhoneConnected.value = connected
        return connected
    }

    fun handleIncomingMessage(messageEvent: MessageEvent) {
        if (messageEvent.path != WearBridgeConstants.PATH_EVENT_CHAT_UPDATED) return
        val raw = messageEvent.data.decodeToString()
        runCatching {
            val dto = json.decodeFromString<WearChatUpdateEvent>(raw)
            val update = WearChatUpdate(
                conversationId = dto.conversationId,
                conversationTitle = dto.conversationTitle,
                updatedMessage = dto.updatedMessage.toDomain(),
                isGenerating = dto.isGenerating
            )
            _chatUpdates.tryEmit(update)
        }.onFailure { t ->
            Log.w(TAG, "Failed decoding WearChatUpdateEvent", t)
        }
    }

    fun handleIncomingChannel(channel: ChannelClient.Channel) {
        val transfer = pendingChannelTransfers[channel.path]
        if (transfer == null) {
            // Orphan channel (e.g. opened after request timeout/cancellation); close immediately to prevent leak
            applicationScope.launch(Dispatchers.IO) {
                runCatching { channelClient.close(channel).await() }
            }
            return
        }
        transfer.channel = channel
        val job = applicationScope.launch(Dispatchers.IO) {
            try {
                val inputStream = channelClient.getInputStream(channel).await()
                val savedFile = inputStream.use { stream ->
                    imageCache.saveFromStream(transfer.cacheKey, stream)
                }
                transfer.deferred.complete(savedFile)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed receiving channel stream ${channel.path}", t)
                transfer.deferred.complete(null)
            } finally {
                pendingChannelTransfers.remove(channel.path, transfer)
                withContext(NonCancellable) {
                    runCatching { channelClient.close(channel).await() }
                }
            }
        }
        transfer.streamJob = job
    }

    suspend fun fetchConversationsPage(
        beforeUpdatedAt: Long? = null,
        beforeId: String? = null,
        pageSize: Int = WearBridgeConstants.DEFAULT_CONVERSATIONS_PAGE_SIZE
    ): WearConversationsPage? {
        val request = WearConversationListRequest(
            beforeUpdatedAt = beforeUpdatedAt,
            beforeId = beforeId,
            pageSize = pageSize
        )
        val responseBytes = sendRpc(
            path = WearBridgeConstants.PATH_RPC_CONVERSATIONS_LIST,
            payload = json.encodeToString(request).encodeToByteArray()
        ) ?: return null

        return runCatching {
            val dto = json.decodeFromString<WearConversationListResponse>(responseBytes.decodeToString())
            WearConversationsPage(
                items = dto.items.map { it.toDomain() },
                hasMore = dto.hasMore,
                nextCursorUpdatedAt = dto.nextCursorUpdatedAt,
                nextCursorId = dto.nextCursorId
            )
        }.getOrNull()
    }

    suspend fun deleteConversation(conversationId: String): WearSimpleResult {
        val request = WearDeleteConversationRequest(conversationId = conversationId)
        return sendSimpleRpc(
            path = WearBridgeConstants.PATH_RPC_CONVERSATIONS_DELETE,
            payload = json.encodeToString(request).encodeToByteArray()
        )
    }

    suspend fun fetchMessagesPage(
        conversationId: String,
        beforePosition: Int? = null,
        pageSize: Int = WearBridgeConstants.DEFAULT_MESSAGES_PAGE_SIZE
    ): WearMessagesPage? {
        val request = WearMessageListRequest(
            conversationId = conversationId,
            beforePosition = beforePosition,
            pageSize = pageSize
        )
        val responseBytes = sendRpc(
            path = WearBridgeConstants.PATH_RPC_MESSAGES_LIST,
            payload = json.encodeToString(request).encodeToByteArray()
        ) ?: return null

        return runCatching {
            val dto = json.decodeFromString<WearMessageListResponse>(responseBytes.decodeToString())
            WearMessagesPage(
                conversationId = dto.conversationId,
                conversationTitle = dto.conversationTitle,
                boundTargetId = dto.boundTargetId,
                boundTargetDisplayName = dto.boundTargetDisplayName,
                messages = dto.messages.map { it.toDomain() },
                hasOlder = dto.hasOlder,
                nextBeforePosition = dto.nextBeforePosition
            )
        }.getOrNull()
    }

    suspend fun sendTurn(
        conversationId: String?,
        userText: String
    ): WearSendTurnResult? {
        val request = WearSendTurnRequest(
            conversationId = conversationId,
            userText = userText
        )
        val responseBytes = sendRpc(
            path = WearBridgeConstants.PATH_RPC_CHAT_SEND,
            payload = json.encodeToString(request).encodeToByteArray()
        ) ?: return null

        return runCatching {
            val dto = json.decodeFromString<WearSendTurnAcceptedResponse>(responseBytes.decodeToString())
            WearSendTurnResult(
                accepted = dto.accepted,
                conversationId = dto.conversationId,
                boundTargetDisplayName = dto.boundTargetDisplayName,
                userMessage = dto.userMessage?.toDomain(),
                assistantPlaceholder = dto.assistantPlaceholder?.toDomain(),
                rejectionCode = dto.rejectionCode
            )
        }.getOrNull()
    }

    suspend fun retryTurn(
        conversationId: String,
        assistantMessageId: String
    ): WearSimpleResult {
        val request = WearRetryTurnRequest(
            conversationId = conversationId,
            assistantMessageId = assistantMessageId
        )
        return sendSimpleRpc(
            path = WearBridgeConstants.PATH_RPC_CHAT_RETRY,
            payload = json.encodeToString(request).encodeToByteArray()
        )
    }

    suspend fun cancelTurn(conversationId: String): WearSimpleResult {
        val request = WearCancelTurnRequest(conversationId = conversationId)
        return sendSimpleRpc(
            path = WearBridgeConstants.PATH_RPC_CHAT_CANCEL,
            payload = json.encodeToString(request).encodeToByteArray()
        )
    }

    suspend fun reportMessage(messageId: String, reason: String): WearSimpleResult {
        val request = WearReportMessageRequest(messageId = messageId, reason = reason)
        return sendSimpleRpc(
            path = WearBridgeConstants.PATH_RPC_CHAT_REPORT,
            payload = json.encodeToString(request).encodeToByteArray()
        )
    }

    suspend fun selectDefaultTarget(targetId: String): WearSimpleResult {
        val request = WearSelectTargetRequest(targetId = targetId)
        val result = sendSimpleRpc(
            path = WearBridgeConstants.PATH_RPC_SETTINGS_SELECT_TARGET,
            payload = json.encodeToString(request).encodeToByteArray()
        )
        if (result.success) {
            _syncedConfig.update { it.copy(selectedTargetId = targetId) }
        }
        return result
    }

    /**
     * Requests a low-res thumbnail for inline chat bubbles.
     * Throttled by [thumbnailSemaphore] (max 2 concurrent streams) to prevent Bluetooth saturation.
     */
    suspend fun requestThumbnail(attachmentId: String, payloadRevision: Int = 0): File? {
        imageCache.getThumbnailFile(attachmentId, payloadRevision)?.let { return it }
        val dedupKey = "${attachmentId}_r${payloadRevision}"
        return deduplicateInFlight(inFlightThumbnails, dedupKey) {
            thumbnailSemaphore.withPermit {
                imageCache.getThumbnailFile(attachmentId, payloadRevision) ?: streamImageFromPhone(
                    attachmentId = attachmentId,
                    kind = WearBridgeConstants.IMAGE_KIND_THUMBNAIL,
                    qualityPreset = WearImageQuality.BALANCED_2000.protocolValue,
                    cacheKey = imageCache.thumbnailKey(attachmentId, payloadRevision)
                )
            }
        }
    }

    /**
     * Requests a high-resolution image when the user taps a thumbnail to open the viewer.
     * Bypasses [thumbnailSemaphore] so user-initiated viewer requests have immediate priority.
     */
    suspend fun requestFullImage(
        attachmentId: String,
        quality: WearImageQuality,
        payloadRevision: Int = 0
    ): File? {
        imageCache.getFullImageFile(attachmentId, quality, payloadRevision)?.let { return it }
        val dedupKey = "${attachmentId}_r${payloadRevision}_${quality.name}"
        return deduplicateInFlight(inFlightFullImages, dedupKey) {
            imageCache.getFullImageFile(attachmentId, quality, payloadRevision) ?: streamImageFromPhone(
                attachmentId = attachmentId,
                kind = WearBridgeConstants.IMAGE_KIND_FULL,
                qualityPreset = quality.protocolValue,
                cacheKey = imageCache.fullImageKey(attachmentId, quality, payloadRevision)
            )
        }
    }

    private suspend fun deduplicateInFlight(
        map: ConcurrentHashMap<String, Deferred<File?>>,
        key: String,
        block: suspend () -> File?
    ): File? {
        lateinit var newDeferred: Deferred<File?>
        newDeferred = applicationScope.async(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                map.remove(key, newDeferred)
                deferredWaiters.remove(newDeferred)
            }
        }
        val active = map.putIfAbsent(key, newDeferred) ?: newDeferred
        val waiterCounter = deferredWaiters.computeIfAbsent(active) { AtomicInteger(0) }
        waiterCounter.incrementAndGet()
        if (active === newDeferred) {
            newDeferred.start()
        } else {
            newDeferred.cancel()
        }
        return try {
            val result = active.await()
            waiterCounter.decrementAndGet()
            result
        } catch (ce: CancellationException) {
            if (waiterCounter.decrementAndGet() <= 0) {
                map.remove(key, active)
                deferredWaiters.remove(active)
                active.cancel()
            }
            throw ce
        }
    }

    private suspend fun streamImageFromPhone(
        attachmentId: String,
        kind: String,
        qualityPreset: String,
        cacheKey: String
    ): File? {
        val requestId = UUID.randomUUID().toString()
        val channelPath = "${WearBridgeConstants.PATH_CHANNEL_IMAGE_PREFIX}$requestId"
        val deferred = CompletableDeferred<File?>()
        val transfer = PendingChannelTransfer(cacheKey = cacheKey, deferred = deferred)
        pendingChannelTransfers[channelPath] = transfer

        val request = WearImageRequest(
            requestId = requestId,
            attachmentId = attachmentId,
            kind = kind,
            qualityPreset = qualityPreset
        )
        val responseBytes = sendRpc(
            path = WearBridgeConstants.PATH_RPC_IMAGE_REQUEST,
            payload = json.encodeToString(request).encodeToByteArray()
        )
        val ack = responseBytes?.let {
            runCatching {
                json.decodeFromString<WearImageRequestAck>(it.decodeToString())
            }.getOrNull()
        }
        if (ack == null || !ack.accepted) {
            abortChannelTransfer(channelPath, transfer)
            return null
        }

        return try {
            withTimeout(IMAGE_STREAM_TIMEOUT_MS) {
                deferred.await()
            }
        } catch (t: Throwable) {
            abortChannelTransfer(channelPath, transfer)
            Log.w(TAG, "Timed out or cancelled waiting for image channel $channelPath", t)
            if (t is CancellationException && t !is kotlinx.coroutines.TimeoutCancellationException) {
                throw t
            }
            null
        }
    }

    private suspend fun abortChannelTransfer(
        channelPath: String,
        transfer: PendingChannelTransfer
    ) {
        pendingChannelTransfers.remove(channelPath, transfer)
        transfer.deferred.cancel()
        transfer.streamJob?.cancel()
        val openChannel = transfer.channel ?: return
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching { channelClient.close(openChannel).await() }
        }
    }

    private suspend fun sendSimpleRpc(path: String, payload: ByteArray): WearSimpleResult {
        val responseBytes = sendRpc(path, payload)
            ?: return WearSimpleResult(success = false, errorCode = "DISCONNECTED")
        return runCatching {
            val dto = json.decodeFromString<WearSimpleAckResponse>(responseBytes.decodeToString())
            WearSimpleResult(
                success = dto.success,
                errorCode = dto.errorCode,
                errorMessage = dto.errorMessage
            )
        }.getOrElse {
            WearSimpleResult(success = false, errorCode = "PARSE_ERROR")
        }
    }

    private suspend fun sendRpc(path: String, payload: ByteArray): ByteArray? {
        val nodeId = resolvePhoneNodeId()
        if (nodeId == null) {
            _isPhoneConnected.value = false
            return null
        }
        _isPhoneConnected.value = true
        return try {
            withTimeout(RPC_TIMEOUT_MS) {
                messageClient.sendRequest(nodeId, path, payload).await()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Wear RPC failed for $path", t)
            checkConnection()
            null
        }
    }

    private suspend fun resolvePhoneNodeId(): String? {
        return runCatching {
            val nodes = nodeClient.connectedNodes.await()
            nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id
        }.getOrNull()
    }

    private fun applySyncedConfigJson(rawJson: String) {
        runCatching {
            val dto = json.decodeFromString<WearSyncedConfigDto>(rawJson)
            _syncedConfig.value = WearSyncedConfig(
                targets = dto.targets.map { target ->
                    WearTargetItem(
                        id = target.id,
                        displayName = target.displayName,
                        subtitle = target.subtitle,
                        isGroup = target.isGroup
                    )
                },
                selectedTargetId = dto.selectedTargetId,
                activeGeneratingConversationIds = dto.activeGeneratingConversationIds,
                updatedAt = dto.updatedAt
            )
        }.onFailure { t ->
            Log.w(TAG, "Failed decoding WearSyncedConfigDto", t)
        }
    }

    private fun WearConversationSummaryDto.toDomain(): WearConversationItem = WearConversationItem(
        id = id,
        title = title,
        activeTargetId = activeTargetId,
        activeTargetDisplayName = activeTargetDisplayName,
        updatedAt = updatedAt,
        isGenerating = isGenerating
    )

    private fun WearMessageDto.toDomain(): WearMessageItem = WearMessageItem(
        id = id,
        conversationId = conversationId,
        position = position,
        turnIndex = turnIndex,
        role = WearMessageRole.fromWire(role),
        textContent = textContent,
        status = WearMessageStatus.fromWire(status),
        errorCode = errorCode,
        errorMessage = errorMessage,
        servedTargetSummary = servedTargetSummary,
        createdAt = createdAt,
        isReported = isReported,
        attachments = attachments.map { att ->
            WearAttachmentItem(
                id = att.id,
                sortIndex = att.sortIndex,
                originalWidth = att.originalWidth,
                originalHeight = att.originalHeight,
                originalMimeType = att.originalMimeType,
                isTextReplaced = att.isTextReplaced,
                payloadRevision = att.payloadRevision
            )
        }
    )

    companion object {
        private const val TAG = "WearPhoneBridgeClient"
        private const val MAX_CONCURRENT_THUMBNAILS = 2
        private const val RPC_TIMEOUT_MS = 12_000L
        private const val IMAGE_STREAM_TIMEOUT_MS = 45_000L
    }
}
