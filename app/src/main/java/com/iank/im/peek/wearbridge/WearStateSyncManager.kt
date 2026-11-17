package com.iank.im.peek.wearbridge

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.iank.im.peek.R
import com.iank.im.peek.di.ApplicationScope
import com.iank.im.peek.domain.model.ChatMessage
import com.iank.im.peek.domain.model.Conversation
import com.iank.im.peek.domain.model.GroupRoutingMode
import com.iank.im.peek.domain.model.MessageAttachment
import com.iank.im.peek.domain.model.MessageRole
import com.iank.im.peek.domain.model.MessageStatus
import com.iank.im.peek.domain.repository.ConversationRepository
import com.iank.im.peek.domain.repository.SettingsRepository
import com.iank.im.peek.domain.usecase.ActiveSendRegistry
import com.iank.im.peek.domain.usecase.LocalizedResourceProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearStateSyncManager @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val activeSendRegistry: ActiveSendRegistry,
    private val resources: LocalizedResourceProvider,
    private val json: Json,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {
    private val started = AtomicBoolean(false)
    private val activeWatchJobs = ConcurrentHashMap<String, Job>()
    private val pendingStopJobs = ConcurrentHashMap<String, Job>()

    fun start() {
        if (!started.compareAndSet(false, true)) return

        applicationScope.launch {
            combine(
                settingsRepository.presets,
                settingsRepository.modelGroups,
                settingsRepository.settings,
                activeSendRegistry.activeConversationIds
            ) { presets, groups, settings, activeIds ->
                val presetMap = presets.associateBy { it.id }
                val targets = buildList {
                    presets.forEach { preset ->
                        add(
                            WearTargetDto(
                                id = preset.id,
                                displayName = preset.alias.ifBlank { preset.modelId },
                                subtitle = "${preset.provider.name} · ${preset.modelId.ifBlank { preset.alias }}",
                                isGroup = false
                            )
                        )
                    }
                    groups.forEach { group ->
                        val memberNames = group.memberPresetIds.mapNotNull { id ->
                            presetMap[id]?.let { p -> p.alias.ifBlank { p.modelId } }
                        }
                        if (memberNames.size >= 2) {
                            val routingLabel = when (group.routingMode) {
                                GroupRoutingMode.FALLBACK -> resources.getString(R.string.group_routing_fallback)
                                GroupRoutingMode.ROUND_ROBIN -> resources.getString(R.string.group_routing_round_robin)
                            }
                            add(
                                WearTargetDto(
                                    id = group.id,
                                    displayName = group.name,
                                    subtitle = "$routingLabel · ${memberNames.joinToString(", ")}",
                                    isGroup = true
                                )
                            )
                        }
                    }
                }
                val validIds = targets.map { it.id }.toSet()
                val selectedId = settings.selectedPresetId?.takeIf { it in validIds }
                    ?: targets.firstOrNull()?.id

                Triple(targets, selectedId, activeIds)
            }.distinctUntilChanged()
                .collect { (targets, selectedId, activeIds) ->
                    val dto = WearSyncedConfigDto(
                        targets = targets,
                        selectedTargetId = selectedId,
                        activeGeneratingConversationIds = activeIds,
                        updatedAt = System.currentTimeMillis()
                    )
                    pushSyncedConfig(dto)
                }
        }

        applicationScope.launch {
            activeSendRegistry.activeConversationIds.collect { activeIds ->
                for (convId in activeIds) {
                    pendingStopJobs.remove(convId)?.cancel()
                    if (!activeWatchJobs.containsKey(convId)) {
                        startObservingConversationUpdates(convId)
                    }
                }
                val removedIds = activeWatchJobs.keys.filter { it !in activeIds }
                for (convId in removedIds) {
                    if (!pendingStopJobs.containsKey(convId)) {
                        pendingStopJobs[convId] = applicationScope.launch {
                            // Keep observer alive briefly so final Room commit after unregister is always pushed
                            delay(POST_GENERATION_GRACE_MS)
                            activeWatchJobs.remove(convId)?.cancel()
                            pendingStopJobs.remove(convId)
                        }
                    }
                }
            }
        }
    }

    private fun startObservingConversationUpdates(conversationId: String) {
        val job = applicationScope.launch {
            var lastPushedSignature: String? = null
            conversationRepository.observeMessages(conversationId).collect { messages ->
                val latestAssistant = messages.lastOrNull { it.role == MessageRole.ASSISTANT }
                    ?: return@collect
                val isGenerating = activeSendRegistry.activeConversationIds.value.contains(conversationId)
                val signature = buildString {
                    append(latestAssistant.id)
                    append('|')
                    append(latestAssistant.status.name)
                    append('|')
                    append(latestAssistant.textContent.hashCode())
                    append('|')
                    append(latestAssistant.isReported)
                    append('|')
                    append(latestAssistant.errorCode)
                    append('|')
                    append(latestAssistant.servedModelName)
                    append('|')
                    append(isGenerating)
                }
                if (signature != lastPushedSignature) {
                    lastPushedSignature = signature
                    val conv = conversationRepository.getConversation(conversationId)
                    val event = WearChatUpdateEvent(
                        conversationId = conversationId,
                        conversationTitle = conv?.title.orEmpty(),
                        updatedMessage = latestAssistant.toWearDto(),
                        isGenerating = isGenerating
                    )
                    pushChatUpdateEvent(event)
                }
            }
        }
        activeWatchJobs[conversationId] = job
    }

    suspend fun notifyMessageUpdated(conversationId: String, messageId: String) {
        val message = conversationRepository.getMessage(messageId) ?: return
        val conv = conversationRepository.getConversation(conversationId)
        val isGenerating = activeSendRegistry.activeConversationIds.value.contains(conversationId)
        val event = WearChatUpdateEvent(
            conversationId = conversationId,
            conversationTitle = conv?.title.orEmpty(),
            updatedMessage = message.toWearDto(),
            isGenerating = isGenerating
        )
        pushChatUpdateEvent(event)
    }

    suspend fun resolveTargetDisplayName(targetId: String?): String? {
        val presets = settingsRepository.getPresets()
        val groups = settingsRepository.getModelGroups()
        val settings = settingsRepository.getSettings()
        val effectiveId = targetId ?: settings.selectedPresetId
        val matchedGroup = groups.firstOrNull { it.id == effectiveId }
        if (matchedGroup != null) return matchedGroup.name
        val matchedPreset = presets.firstOrNull { it.id == effectiveId } ?: presets.firstOrNull()
        return matchedPreset?.let { it.alias.ifBlank { it.modelId } }
    }

    suspend fun buildConversationSummaryDto(conversation: Conversation): WearConversationSummaryDto {
        val activeIds = activeSendRegistry.activeConversationIds.value
        val displayName = resolveTargetDisplayName(conversation.activeTargetId)
        return WearConversationSummaryDto(
            id = conversation.id,
            title = conversation.title,
            activeTargetId = conversation.activeTargetId,
            activeTargetDisplayName = displayName,
            updatedAt = conversation.updatedAt,
            isGenerating = conversation.id in activeIds
        )
    }

    fun ChatMessage.toWearDto(): WearMessageDto {
        val servedSummary = if (role == MessageRole.ASSISTANT) {
            val groupName = servedGroupName?.takeIf { it.isNotBlank() }
            val presetName = servedPresetName?.takeIf { it.isNotBlank() }
            val modelName = (servedModelName ?: modelIdUsed)?.takeIf { it.isNotBlank() }
            when {
                groupName != null && modelName != null ->
                    "$groupName · ${presetName ?: modelName} ($modelName)"
                presetName != null && modelName != null ->
                    "$presetName ($modelName)"
                modelName != null ->
                    modelName
                else -> null
            }
        } else {
            null
        }
        return WearMessageDto(
            id = id,
            conversationId = conversationId,
            position = position,
            turnIndex = turnIndex,
            role = role.name,
            textContent = textContent,
            status = status.name,
            errorCode = errorCode,
            errorMessage = errorMessage,
            servedTargetSummary = servedSummary,
            createdAt = createdAt,
            isReported = isReported,
            attachments = attachments.map { it.toWearDto() }
        )
    }

    private fun MessageAttachment.toWearDto(): WearAttachmentDto = WearAttachmentDto(
        id = id,
        sortIndex = sortIndex,
        originalWidth = originalWidth,
        originalHeight = originalHeight,
        originalMimeType = originalMimeType,
        isTextReplaced = isTextReplaced
    )

    private suspend fun pushSyncedConfig(dto: WearSyncedConfigDto) {
        runCatching {
            val payloadJson = json.encodeToString(dto)
            val request = PutDataMapRequest.create(WearBridgeConstants.PATH_STATE_CONFIG).apply {
                dataMap.putString(WearBridgeConstants.KEY_CONFIG_JSON, payloadJson)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(appContext).putDataItem(request).await()
        }.onFailure { t ->
            Log.w(TAG, "Failed to push synced config to Wear DataClient", t)
        }
    }

    private suspend fun pushChatUpdateEvent(event: WearChatUpdateEvent) {
        runCatching {
            val bytes = json.encodeToString(event).encodeToByteArray()
            val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
            val messageClient = Wearable.getMessageClient(appContext)
            for (node in nodes) {
                runCatching {
                    messageClient.sendMessage(
                        node.id,
                        WearBridgeConstants.PATH_EVENT_CHAT_UPDATED,
                        bytes
                    ).await()
                }
            }
        }.onFailure { t ->
            Log.w(TAG, "Failed to push chat update event to watch", t)
        }
    }

    companion object {
        private const val TAG = "WearStateSyncManager"
        private const val POST_GENERATION_GRACE_MS = 5_000L
    }
}
