package com.iank.im.peek.wearbridge

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.WearableListenerService
import com.iank.im.peek.data.local.maintenance.StartupMaintenance
import com.iank.im.peek.di.ApplicationScope
import com.iank.im.peek.domain.model.MessageRole
import com.iank.im.peek.domain.model.MessageStatus
import com.iank.im.peek.domain.repository.ConversationRepository
import com.iank.im.peek.domain.repository.SettingsRepository
import com.iank.im.peek.domain.usecase.ActiveSendRegistry
import com.iank.im.peek.domain.usecase.DeleteConversationUseCase
import com.iank.im.peek.domain.usecase.ExecutionTargetResolution
import com.iank.im.peek.domain.usecase.SendChatMessageUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class WearBridgeListenerService : WearableListenerService() {

    @Inject
    lateinit var conversationRepository: ConversationRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var sendChatMessageUseCase: SendChatMessageUseCase

    @Inject
    lateinit var deleteConversationUseCase: DeleteConversationUseCase

    @Inject
    lateinit var activeSendRegistry: ActiveSendRegistry

    @Inject
    lateinit var startupMaintenance: StartupMaintenance

    @Inject
    lateinit var wearStateSyncManager: WearStateSyncManager

    @Inject
    lateinit var wearImageStreamHelper: WearImageStreamHelper

    @Inject
    lateinit var wearReportDispatcher: WearReportDispatcher

    @Inject
    lateinit var json: Json

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        wearStateSyncManager.start()
        applicationScope.launch {
            startupMaintenance.runOnStartupOnce()
        }
    }

    override fun onRequest(nodeId: String, path: String, data: ByteArray): Task<ByteArray> {
        val taskSource = TaskCompletionSource<ByteArray>()
        applicationScope.launch(Dispatchers.IO) {
            try {
                startupMaintenance.runOnStartupOnce()
                val responseBytes = dispatchRpc(nodeId, path, data)
                taskSource.setResult(responseBytes)
            } catch (t: Throwable) {
                Log.e(TAG, "Error handling Wear RPC $path", t)
                val fallback = json.encodeToString(
                    WearSimpleAckResponse(
                        success = false,
                        errorCode = "INTERNAL_ERROR",
                        errorMessage = t.message
                    )
                ).encodeToByteArray()
                taskSource.setResult(fallback)
            }
        }
        return taskSource.task
    }

    private suspend fun dispatchRpc(nodeId: String, path: String, data: ByteArray): ByteArray {
        val payloadText = data.decodeToString()
        return when (path) {
            WearBridgeConstants.PATH_RPC_CONVERSATIONS_LIST -> {
                val req = if (payloadText.isBlank()) {
                    WearConversationListRequest()
                } else {
                    json.decodeFromString<WearConversationListRequest>(payloadText)
                }
                val page = conversationRepository.getConversationsPage(
                    beforeUpdatedAt = req.beforeUpdatedAt,
                    beforeId = req.beforeId,
                    pageSize = req.pageSize
                )
                val summaries = page.items.map { wearStateSyncManager.buildConversationSummaryDto(it) }
                json.encodeToString(
                    WearConversationListResponse(
                        items = summaries,
                        hasMore = page.hasMore,
                        nextCursorUpdatedAt = page.nextCursorUpdatedAt,
                        nextCursorId = page.nextCursorId
                    )
                ).encodeToByteArray()
            }

            WearBridgeConstants.PATH_RPC_CONVERSATIONS_DELETE -> {
                val req = json.decodeFromString<WearDeleteConversationRequest>(payloadText)
                deleteConversationUseCase(req.conversationId)
                json.encodeToString(WearSimpleAckResponse(success = true)).encodeToByteArray()
            }

            WearBridgeConstants.PATH_RPC_MESSAGES_LIST -> {
                val req = json.decodeFromString<WearMessageListRequest>(payloadText)
                val page = conversationRepository.getMessagesPage(
                    conversationId = req.conversationId,
                    beforePosition = req.beforePosition,
                    pageSize = req.pageSize
                )
                val boundTargetId = page.conversation?.activeTargetId
                val boundTargetName = wearStateSyncManager.resolveTargetDisplayName(boundTargetId)
                val wearMessages = with(wearStateSyncManager) {
                    page.itemsAscending.map { it.toWearDto() }
                }
                json.encodeToString(
                    WearMessageListResponse(
                        conversationId = req.conversationId,
                        conversationTitle = page.conversation?.title.orEmpty(),
                        boundTargetId = boundTargetId,
                        boundTargetDisplayName = boundTargetName,
                        messages = wearMessages,
                        hasOlder = page.hasOlder,
                        nextBeforePosition = page.nextBeforePosition
                    )
                ).encodeToByteArray()
            }

            WearBridgeConstants.PATH_RPC_CHAT_SEND -> {
                val req = json.decodeFromString<WearSendTurnRequest>(payloadText)
                handleSendTurn(req)
            }

            WearBridgeConstants.PATH_RPC_CHAT_RETRY -> {
                val req = json.decodeFromString<WearRetryTurnRequest>(payloadText)
                handleRetryTurn(req)
            }

            WearBridgeConstants.PATH_RPC_CHAT_CANCEL -> {
                val req = json.decodeFromString<WearCancelTurnRequest>(payloadText)
                handleCancelTurn(req)
            }

            WearBridgeConstants.PATH_RPC_CHAT_REPORT -> {
                val req = json.decodeFromString<WearReportMessageRequest>(payloadText)
                val ack = wearReportDispatcher.dispatchReport(req.messageId, req.reason)
                json.encodeToString(ack).encodeToByteArray()
            }

            WearBridgeConstants.PATH_RPC_SETTINGS_SELECT_TARGET -> {
                val req = json.decodeFromString<WearSelectTargetRequest>(payloadText)
                settingsRepository.selectPreset(req.targetId)
                json.encodeToString(WearSimpleAckResponse(success = true)).encodeToByteArray()
            }

            WearBridgeConstants.PATH_RPC_IMAGE_REQUEST -> {
                val req = json.decodeFromString<WearImageRequest>(payloadText)
                val ack = wearImageStreamHelper.handleImageRequest(nodeId, req)
                json.encodeToString(ack).encodeToByteArray()
            }

            else -> {
                json.encodeToString(
                    WearSimpleAckResponse(
                        success = false,
                        errorCode = "UNKNOWN_PATH",
                        errorMessage = "Unsupported RPC path: $path"
                    )
                ).encodeToByteArray()
            }
        }
    }

    private suspend fun handleSendTurn(req: WearSendTurnRequest): ByteArray {
        if (req.userText.isBlank()) {
            return json.encodeToString(
                WearSendTurnAcceptedResponse(
                    accepted = false,
                    rejectionCode = "EMPTY"
                )
            ).encodeToByteArray()
        }

        val existingConv = req.conversationId?.let { conversationRepository.getConversation(it) }
        val requestedTargetId = if (req.conversationId != null) {
            existingConv?.activeTargetId
        } else {
            null
        }

        val resolution = sendChatMessageUseCase.prepareExecutionTarget(requestedTargetId)
        val executionTarget = when (resolution) {
            is ExecutionTargetResolution.Ready -> resolution.target
            ExecutionTargetResolution.NoPresetConfigured -> {
                return json.encodeToString(
                    WearSendTurnAcceptedResponse(accepted = false, rejectionCode = "NO_PRESET")
                ).encodeToByteArray()
            }
            ExecutionTargetResolution.MissingModelId -> {
                return json.encodeToString(
                    WearSendTurnAcceptedResponse(accepted = false, rejectionCode = "MISSING_MODEL")
                ).encodeToByteArray()
            }
            ExecutionTargetResolution.MissingApiKey -> {
                return json.encodeToString(
                    WearSendTurnAcceptedResponse(accepted = false, rejectionCode = "MISSING_API_KEY")
                ).encodeToByteArray()
            }
        }

        val resolvedConvId = sendChatMessageUseCase.sendNewTurn(
            conversationId = req.conversationId,
            userText = req.userText,
            pendingAttachments = emptyList(),
            executionTarget = executionTarget
        )

        val latest = conversationRepository.getMessagesPage(
            conversationId = resolvedConvId,
            beforePosition = null,
            pageSize = 2
        )
        val userDto = latest.itemsAscending
            .lastOrNull { it.role == MessageRole.USER }
            ?.let { with(wearStateSyncManager) { it.toWearDto() } }
        val assistantDto = latest.itemsAscending
            .lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.let { with(wearStateSyncManager) { it.toWearDto() } }
        val boundTargetDisplayName = wearStateSyncManager.resolveTargetDisplayName(executionTarget.targetId)

        return json.encodeToString(
            WearSendTurnAcceptedResponse(
                accepted = true,
                conversationId = resolvedConvId,
                boundTargetDisplayName = boundTargetDisplayName,
                userMessage = userDto,
                assistantPlaceholder = assistantDto
            )
        ).encodeToByteArray()
    }

    private suspend fun handleRetryTurn(req: WearRetryTurnRequest): ByteArray {
        val conv = conversationRepository.getConversation(req.conversationId)
        val resolution = sendChatMessageUseCase.prepareExecutionTarget(conv?.activeTargetId)
        val executionTarget = when (resolution) {
            is ExecutionTargetResolution.Ready -> resolution.target
            ExecutionTargetResolution.NoPresetConfigured -> {
                return json.encodeToString(
                    WearSimpleAckResponse(success = false, errorCode = "NO_PRESET")
                ).encodeToByteArray()
            }
            ExecutionTargetResolution.MissingModelId -> {
                return json.encodeToString(
                    WearSimpleAckResponse(success = false, errorCode = "MISSING_MODEL")
                ).encodeToByteArray()
            }
            ExecutionTargetResolution.MissingApiKey -> {
                return json.encodeToString(
                    WearSimpleAckResponse(success = false, errorCode = "MISSING_API_KEY")
                ).encodeToByteArray()
            }
        }

        sendChatMessageUseCase.retryAssistantMessage(
            assistantMessageId = req.assistantMessageId,
            executionTarget = executionTarget
        )
        wearStateSyncManager.notifyMessageUpdated(req.conversationId, req.assistantMessageId)
        return json.encodeToString(WearSimpleAckResponse(success = true)).encodeToByteArray()
    }

    private suspend fun handleCancelTurn(req: WearCancelTurnRequest): ByteArray {
        sendChatMessageUseCase.cancelConversationRequest(req.conversationId)
        val loadingMessages = conversationRepository.getMessages(req.conversationId)
            .filter { it.status == MessageStatus.LOADING }
        for (msg in loadingMessages) {
            if (!activeSendRegistry.isMessageActivelySending(msg.id)) {
                conversationRepository.updateAssistantCancelled(msg.id)
                wearStateSyncManager.notifyMessageUpdated(req.conversationId, msg.id)
            }
        }
        return json.encodeToString(WearSimpleAckResponse(success = true)).encodeToByteArray()
    }

    companion object {
        private const val TAG = "WearBridgeListener"
    }
}
