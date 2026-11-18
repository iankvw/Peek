package com.iank.im.peek.wear.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iank.im.peek.wear.data.bridge.WearBridgeConstants
import com.iank.im.peek.wear.data.bridge.WearPhoneBridgeClient
import com.iank.im.peek.wear.data.cache.WearImageCache
import com.iank.im.peek.wear.data.datastore.WearSettingsDataStore
import com.iank.im.peek.wear.domain.model.WearChatUpdate
import com.iank.im.peek.wear.domain.model.WearInputMode
import com.iank.im.peek.wear.domain.model.WearMessageItem
import com.iank.im.peek.wear.domain.model.WearMessageRole
import com.iank.im.peek.wear.domain.model.WearMessageStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class WearChatUiState(
    val conversationId: String? = null,
    val conversationTitle: String? = null,
    val boundTargetDisplayName: String? = null,
    val messages: List<WearMessageItem> = emptyList(),
    val thumbnailFiles: Map<String, File> = emptyMap(),
    val hasOlder: Boolean = false,
    val nextBeforePosition: Int? = null,
    val isLoadingInitial: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val isSendingTurn: Boolean = false,
    val isGenerating: Boolean = false,
    val inputMode: WearInputMode = WearInputMode.TEXT_KEYBOARD,
    val revealedReportedIds: Set<String> = emptySet(),
    val selectedMessageForMenu: WearMessageItem? = null,
    val errorBannerCode: String? = null,
    val scrollToBottomTick: Int = 0
)

sealed interface WearChatScrollEvent {
    data class PrependedOlderMessages(val addedCount: Int) : WearChatScrollEvent
}

@HiltViewModel
class WearChatViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val bridgeClient: WearPhoneBridgeClient,
    private val imageCache: WearImageCache,
    private val settingsDataStore: WearSettingsDataStore
) : ViewModel() {

    private val initialConversationId: String? =
        savedStateHandle.get<String>("conversationId")?.takeIf { it.isNotBlank() }

    val defaultInputMethod: StateFlow<WearInputMode> = settingsDataStore.defaultInputMethod
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = settingsDataStore.defaultInputMethod.value
        )

    private val _uiState = MutableStateFlow(
        WearChatUiState(
            conversationId = initialConversationId,
            isLoadingInitial = initialConversationId != null,
            inputMode = settingsDataStore.defaultInputMethod.value
        )
    )
    val uiState: StateFlow<WearChatUiState> = _uiState.asStateFlow()

    private val _scrollEvents = MutableSharedFlow<WearChatScrollEvent>(extraBufferCapacity = 8)
    val scrollEvents: SharedFlow<WearChatScrollEvent> = _scrollEvents.asSharedFlow()

    private val requestedThumbIds = mutableSetOf<String>()
    private val pendingUpdatesForNewConv = mutableListOf<WearChatUpdate>()
    private var reconcileJob: Job? = null

    init {
        observeInputMode()
        observeSyncedConfig()
        observeChatUpdates()
        if (initialConversationId != null) {
            loadInitialMessages(initialConversationId)
        }
    }

    private fun observeInputMode() {
        viewModelScope.launch {
            defaultInputMethod.collect { mode ->
                _uiState.update { it.copy(inputMode = mode) }
            }
        }
    }

    private fun observeSyncedConfig() {
        viewModelScope.launch {
            bridgeClient.syncedConfig.collect { config ->
                var needsReconcileConvId: String? = null
                _uiState.update { state ->
                    val convId = state.conversationId
                    val isActiveOnPhone = convId != null && convId in config.activeGeneratingConversationIds
                    val fallbackTargetName = if (convId == null && state.boundTargetDisplayName == null) {
                        config.selectedTarget?.displayName
                    } else {
                        state.boundTargetDisplayName
                    }
                    val derivedGenerating = deriveIsGenerating(state.messages, isActiveOnPhone)
                    if (convId != null &&
                        !isActiveOnPhone &&
                        state.messages.any { it.status == WearMessageStatus.LOADING }
                    ) {
                        needsReconcileConvId = convId
                    }
                    state.copy(
                        boundTargetDisplayName = fallbackTargetName,
                        isGenerating = derivedGenerating
                    )
                }
                needsReconcileConvId?.let { scheduleReconcile(it) }
            }
        }
    }

    private fun observeChatUpdates() {
        viewModelScope.launch {
            bridgeClient.chatUpdates.collect { update ->
                val state = _uiState.value
                val currentConvId = state.conversationId
                if (currentConvId == null) {
                    if (state.isSendingTurn) {
                        synchronized(pendingUpdatesForNewConv) {
                            pendingUpdatesForNewConv += update
                        }
                    }
                    return@collect
                }
                if (update.conversationId != currentConvId) return@collect
                applyChatUpdate(update)
            }
        }
    }

    private fun applyChatUpdate(update: WearChatUpdate) {
        reconcileJob?.cancel()
        val isActiveOnPhone = update.isGenerating &&
            update.updatedMessage.status == WearMessageStatus.LOADING
        _uiState.update { state ->
            val updatedList = mergeMessages(state.messages, listOf(update.updatedMessage))
            val isGenerating = deriveIsGenerating(updatedList, isActiveOnPhone)
            state.copy(
                conversationTitle = update.conversationTitle.ifBlank { state.conversationTitle },
                messages = updatedList,
                isGenerating = isGenerating
            )
        }
        ensureThumbnailsForMessages(listOf(update.updatedMessage))
    }

    private fun scheduleReconcile(conversationId: String) {
        reconcileJob?.cancel()
        reconcileJob = viewModelScope.launch {
            delay(RECONCILE_DELAY_MS)
            val currentState = _uiState.value
            if (currentState.conversationId != conversationId) return@launch
            if (currentState.messages.none { it.status == WearMessageStatus.LOADING }) return@launch
            val page = bridgeClient.fetchMessagesPage(
                conversationId = conversationId,
                beforePosition = null,
                pageSize = WearBridgeConstants.DEFAULT_MESSAGES_PAGE_SIZE
            ) ?: return@launch
            val isActiveOnPhone = conversationId in bridgeClient.syncedConfig.value.activeGeneratingConversationIds
            _uiState.update { state ->
                val merged = mergeMessages(state.messages, page.messages)
                state.copy(
                    conversationTitle = page.conversationTitle.ifBlank { state.conversationTitle },
                    boundTargetDisplayName = page.boundTargetDisplayName ?: state.boundTargetDisplayName,
                    messages = merged,
                    hasOlder = state.hasOlder || page.hasOlder,
                    nextBeforePosition = state.nextBeforePosition ?: page.nextBeforePosition,
                    isGenerating = deriveIsGenerating(merged, isActiveOnPhone)
                )
            }
            ensureThumbnailsForMessages(page.messages)
        }
    }

    private fun deriveIsGenerating(
        messages: List<WearMessageItem>,
        isActiveOnPhone: Boolean
    ): Boolean {
        val lastAssistant = messages.lastOrNull { it.role == WearMessageRole.ASSISTANT }
        if (lastAssistant != null && lastAssistant.status != WearMessageStatus.LOADING) {
            return false
        }
        return messages.any { it.status == WearMessageStatus.LOADING } || isActiveOnPhone
    }

    private fun mergeMessages(
        existing: List<WearMessageItem>,
        incoming: List<WearMessageItem>
    ): List<WearMessageItem> {
        if (incoming.isEmpty()) return existing
        val byId = LinkedHashMap<String, WearMessageItem>(existing.size + incoming.size)
        for (msg in existing) {
            byId[msg.id] = msg
        }
        for (msg in incoming) {
            byId[msg.id] = msg
        }
        return byId.values.sortedBy { it.position }
    }

    private fun loadInitialMessages(conversationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInitial = true, errorBannerCode = null) }
            val page = bridgeClient.fetchMessagesPage(
                conversationId = conversationId,
                beforePosition = null,
                pageSize = WearBridgeConstants.DEFAULT_MESSAGES_PAGE_SIZE
            )
            if (page == null) {
                _uiState.update {
                    it.copy(
                        isLoadingInitial = false,
                        errorBannerCode = "DISCONNECTED"
                    )
                }
                return@launch
            }
            val activeIds = bridgeClient.syncedConfig.value.activeGeneratingConversationIds
            _uiState.update { state ->
                state.copy(
                    conversationTitle = page.conversationTitle.ifBlank { state.conversationTitle },
                    boundTargetDisplayName = page.boundTargetDisplayName ?: state.boundTargetDisplayName,
                    messages = page.messages,
                    hasOlder = page.hasOlder,
                    nextBeforePosition = page.nextBeforePosition,
                    isLoadingInitial = false,
                    isGenerating = deriveIsGenerating(page.messages, conversationId in activeIds),
                    scrollToBottomTick = state.scrollToBottomTick + 1
                )
            }
            ensureThumbnailsForMessages(page.messages)
        }
    }

    fun loadOlderMessages() {
        val current = _uiState.value
        val convId = current.conversationId ?: return
        if (!current.hasOlder || current.isLoadingOlder || current.isLoadingInitial) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOlder = true) }
            val page = bridgeClient.fetchMessagesPage(
                conversationId = convId,
                beforePosition = current.nextBeforePosition,
                pageSize = WearBridgeConstants.DEFAULT_MESSAGES_PAGE_SIZE
            )
            if (page == null) {
                _uiState.update { it.copy(isLoadingOlder = false) }
                return@launch
            }
            var addedCount = 0
            _uiState.update { state ->
                val existingIds = state.messages.map { it.id }.toSet()
                val olderUnique = page.messages.filter { it.id !in existingIds }
                addedCount = olderUnique.size
                state.copy(
                    messages = olderUnique + state.messages,
                    hasOlder = page.hasOlder,
                    nextBeforePosition = page.nextBeforePosition,
                    isLoadingOlder = false
                )
            }
            if (addedCount > 0) {
                _scrollEvents.tryEmit(WearChatScrollEvent.PrependedOlderMessages(addedCount))
            }
            ensureThumbnailsForMessages(page.messages)
        }
    }

    private fun ensureThumbnailsForMessages(messages: List<WearMessageItem>) {
        val attachments = messages.flatMap { it.attachments }
        if (attachments.isEmpty()) return

        val immediateCached = mutableMapOf<String, File>()
        val toFetch = mutableListOf<String>()
        for (att in attachments) {
            val cached = imageCache.getThumbnailFile(att.id)
            if (cached != null) {
                immediateCached[att.id] = cached
            } else if (requestedThumbIds.add(att.id)) {
                toFetch += att.id
            }
        }
        if (immediateCached.isNotEmpty()) {
            _uiState.update { state ->
                state.copy(thumbnailFiles = state.thumbnailFiles + immediateCached)
            }
        }
        for (attachmentId in toFetch) {
            viewModelScope.launch {
                val file = bridgeClient.requestThumbnail(attachmentId)
                if (file != null) {
                    _uiState.update { state ->
                        state.copy(thumbnailFiles = state.thumbnailFiles + (attachmentId to file))
                    }
                } else {
                    requestedThumbIds.remove(attachmentId)
                }
            }
        }
    }

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _uiState.value.isSendingTurn) return

        viewModelScope.launch {
            val sendingNewConversation = _uiState.value.conversationId == null
            if (sendingNewConversation) {
                synchronized(pendingUpdatesForNewConv) {
                    pendingUpdatesForNewConv.clear()
                }
            }
            _uiState.update { it.copy(isSendingTurn = true, errorBannerCode = null) }
            try {
                val result = bridgeClient.sendTurn(
                    conversationId = _uiState.value.conversationId,
                    userText = trimmed
                )
                if (result == null) {
                    _uiState.update {
                        it.copy(
                            isSendingTurn = false,
                            errorBannerCode = "DISCONNECTED"
                        )
                    }
                    return@launch
                }
                if (!result.accepted) {
                    _uiState.update {
                        it.copy(
                            isSendingTurn = false,
                            errorBannerCode = result.rejectionCode ?: "UNKNOWN"
                        )
                    }
                    return@launch
                }

                val resolvedConvId = result.conversationId ?: _uiState.value.conversationId
                if (resolvedConvId != null) {
                    savedStateHandle["conversationId"] = resolvedConvId
                }

                val bufferedForConv = synchronized(pendingUpdatesForNewConv) {
                    val matched = if (resolvedConvId != null) {
                        pendingUpdatesForNewConv.filter { it.conversationId == resolvedConvId }
                    } else {
                        emptyList()
                    }
                    pendingUpdatesForNewConv.clear()
                    matched
                }

                val activeIds = bridgeClient.syncedConfig.value.activeGeneratingConversationIds
                val isActiveOnPhone = resolvedConvId != null && resolvedConvId in activeIds
                val latestTitle = bufferedForConv.lastOrNull()?.conversationTitle

                _uiState.update { state ->
                    val added = listOfNotNull(result.userMessage, result.assistantPlaceholder) +
                        bufferedForConv.map { it.updatedMessage }
                    val newMessages = mergeMessages(state.messages, added)
                    val derivedGen = deriveIsGenerating(newMessages, isActiveOnPhone)
                    state.copy(
                        conversationId = resolvedConvId,
                        conversationTitle = latestTitle?.takeIf { it.isNotBlank() } ?: state.conversationTitle,
                        boundTargetDisplayName = result.boundTargetDisplayName ?: state.boundTargetDisplayName,
                        messages = newMessages,
                        isSendingTurn = false,
                        isGenerating = derivedGen,
                        scrollToBottomTick = state.scrollToBottomTick + 1
                    )
                }

                if (resolvedConvId != null && _uiState.value.isGenerating) {
                    scheduleReconcile(resolvedConvId)
                }
            } finally {
                _uiState.update { it.copy(isSendingTurn = false) }
            }
        }
    }

    fun retryMessage(assistantMessageId: String) {
        val convId = _uiState.value.conversationId ?: return
        viewModelScope.launch {
            _uiState.update { state ->
                val updatedMsgs = state.messages.map { msg ->
                    if (msg.id == assistantMessageId) {
                        msg.copy(
                            status = WearMessageStatus.LOADING,
                            errorCode = null,
                            errorMessage = null
                        )
                    } else {
                        msg
                    }
                }
                state.copy(
                    isGenerating = true,
                    errorBannerCode = null,
                    messages = updatedMsgs
                )
            }
            val result = bridgeClient.retryTurn(convId, assistantMessageId)
            if (!result.success) {
                _uiState.update { state ->
                    state.copy(
                        isGenerating = deriveIsGenerating(state.messages, isActiveOnPhone = false),
                        errorBannerCode = result.errorCode ?: "DISCONNECTED"
                    )
                }
            }
        }
    }

    fun cancelGeneration() {
        val convId = _uiState.value.conversationId ?: return
        viewModelScope.launch {
            val result = bridgeClient.cancelTurn(convId)
            if (result.success) {
                scheduleReconcile(convId)
            }
        }
    }

    fun toggleReportedMessageReveal(messageId: String) {
        _uiState.update { state ->
            val next = if (messageId in state.revealedReportedIds) {
                state.revealedReportedIds - messageId
            } else {
                state.revealedReportedIds + messageId
            }
            state.copy(revealedReportedIds = next)
        }
    }

    fun openMessageContextMenu(message: WearMessageItem) {
        _uiState.update { it.copy(selectedMessageForMenu = message) }
    }

    fun dismissMessageContextMenu() {
        _uiState.update { it.copy(selectedMessageForMenu = null) }
    }

    fun dismissErrorBanner() {
        _uiState.update { it.copy(errorBannerCode = null) }
    }

    companion object {
        private const val RECONCILE_DELAY_MS = 1_500L
    }
}
