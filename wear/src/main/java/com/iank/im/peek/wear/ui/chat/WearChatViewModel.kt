package com.iank.im.peek.wear.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iank.im.peek.wear.data.bridge.WearBridgeConstants
import com.iank.im.peek.wear.data.bridge.WearPhoneBridgeClient
import com.iank.im.peek.wear.data.cache.WearImageCache
import com.iank.im.peek.wear.data.datastore.WearSettingsDataStore
import com.iank.im.peek.wear.domain.model.WearInputMode
import com.iank.im.peek.wear.domain.model.WearMessageItem
import com.iank.im.peek.wear.domain.model.WearMessageStatus
import dagger.hilt.android.lifecycle.HiltViewModel
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
    savedStateHandle: SavedStateHandle,
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
                _uiState.update { state ->
                    val convId = state.conversationId
                    val isGenFromRegistry = convId != null && convId in config.activeGeneratingConversationIds
                    val hasLoadingMsg = state.messages.any { it.status == WearMessageStatus.LOADING }
                    val fallbackTargetName = if (convId == null && state.boundTargetDisplayName == null) {
                        config.selectedTarget?.displayName
                    } else {
                        state.boundTargetDisplayName
                    }
                    state.copy(
                        boundTargetDisplayName = fallbackTargetName,
                        isGenerating = isGenFromRegistry || hasLoadingMsg
                    )
                }
            }
        }
    }

    private fun observeChatUpdates() {
        viewModelScope.launch {
            bridgeClient.chatUpdates.collect { update ->
                val currentConvId = _uiState.value.conversationId ?: return@collect
                if (update.conversationId != currentConvId) return@collect

                _uiState.update { state ->
                    val existingIdx = state.messages.indexOfFirst { it.id == update.updatedMessage.id }
                    val updatedList = if (existingIdx >= 0) {
                        state.messages.toMutableList().apply { set(existingIdx, update.updatedMessage) }
                    } else {
                        (state.messages + update.updatedMessage).sortedBy { it.position }
                    }
                    val hasLoading = updatedList.any { it.status == WearMessageStatus.LOADING }
                    state.copy(
                        conversationTitle = update.conversationTitle.ifBlank { state.conversationTitle },
                        messages = updatedList,
                        isGenerating = update.isGenerating || hasLoading
                    )
                }
                ensureThumbnailsForMessages(listOf(update.updatedMessage))
            }
        }
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
            val hasLoading = page.messages.any { it.status == WearMessageStatus.LOADING }
            _uiState.update { state ->
                state.copy(
                    conversationTitle = page.conversationTitle.ifBlank { state.conversationTitle },
                    boundTargetDisplayName = page.boundTargetDisplayName ?: state.boundTargetDisplayName,
                    messages = page.messages,
                    hasOlder = page.hasOlder,
                    nextBeforePosition = page.nextBeforePosition,
                    isLoadingInitial = false,
                    isGenerating = conversationId in activeIds || hasLoading,
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
            _uiState.update { it.copy(isSendingTurn = true, errorBannerCode = null) }
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

            _uiState.update { state ->
                val added = listOfNotNull(result.userMessage, result.assistantPlaceholder)
                val existingIds = state.messages.map { it.id }.toSet()
                val newMessages = state.messages + added.filter { it.id !in existingIds }
                state.copy(
                    conversationId = result.conversationId ?: state.conversationId,
                    boundTargetDisplayName = result.boundTargetDisplayName ?: state.boundTargetDisplayName,
                    messages = newMessages,
                    isSendingTurn = false,
                    isGenerating = true,
                    scrollToBottomTick = state.scrollToBottomTick + 1
                )
            }
        }
    }

    fun retryMessage(assistantMessageId: String) {
        val convId = _uiState.value.conversationId ?: return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isGenerating = true,
                    errorBannerCode = null,
                    messages = state.messages.map { msg ->
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
                )
            }
            val result = bridgeClient.retryTurn(convId, assistantMessageId)
            if (!result.success) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorBannerCode = result.errorCode ?: "DISCONNECTED"
                    )
                }
            }
        }
    }

    fun cancelGeneration() {
        val convId = _uiState.value.conversationId ?: return
        viewModelScope.launch {
            bridgeClient.cancelTurn(convId)
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
}
