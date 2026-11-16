package com.iank.im.peek.wear.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iank.im.peek.wear.data.bridge.WearBridgeConstants
import com.iank.im.peek.wear.data.bridge.WearPhoneBridgeClient
import com.iank.im.peek.wear.domain.model.WearConversationItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WearHomeUiState(
    val conversations: List<WearConversationItem> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursorUpdatedAt: Long? = null,
    val nextCursorId: String? = null,
    val isLoadingInitial: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isConnected: Boolean = true,
    val hasConfiguredTarget: Boolean = true,
    val pendingDeleteConversation: WearConversationItem? = null,
    val showNoPresetNotice: Boolean = false
)

@HiltViewModel
class WearHomeViewModel @Inject constructor(
    private val bridgeClient: WearPhoneBridgeClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(WearHomeUiState())
    val uiState: StateFlow<WearHomeUiState> = _uiState.asStateFlow()

    init {
        observeBridgeState()
        refreshConversations()
    }

    private fun observeBridgeState() {
        viewModelScope.launch {
            bridgeClient.syncedConfig.collect { config ->
                _uiState.update { state ->
                    val updatedList = state.conversations.map { item ->
                        item.copy(isGenerating = item.id in config.activeGeneratingConversationIds)
                    }
                    state.copy(
                        conversations = updatedList,
                        hasConfiguredTarget = config.targets.isNotEmpty()
                    )
                }
            }
        }

        viewModelScope.launch {
            bridgeClient.chatUpdates.collect { update ->
                var foundExisting = false
                _uiState.update { state ->
                    val updatedList = state.conversations.map { item ->
                        if (item.id == update.conversationId) {
                            foundExisting = true
                            item.copy(
                                title = update.conversationTitle.ifBlank { item.title },
                                updatedAt = maxOf(item.updatedAt, update.updatedMessage.createdAt),
                                isGenerating = update.isGenerating
                            )
                        } else {
                            item
                        }
                    }.sortedByDescending { it.updatedAt }
                    state.copy(conversations = updatedList)
                }
                if (!foundExisting) {
                    refreshConversations()
                }
            }
        }
    }

    fun refreshConversations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInitial = it.conversations.isEmpty()) }
            val connected = bridgeClient.checkConnection()
            if (!connected) {
                _uiState.update {
                    it.copy(
                        isLoadingInitial = false,
                        isConnected = false
                    )
                }
                return@launch
            }
            bridgeClient.refreshInitialSyncedConfig()
            val page = bridgeClient.fetchConversationsPage(
                beforeUpdatedAt = null,
                beforeId = null,
                pageSize = WearBridgeConstants.DEFAULT_CONVERSATIONS_PAGE_SIZE
            )
            if (page == null) {
                _uiState.update {
                    it.copy(
                        isLoadingInitial = false,
                        isConnected = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        conversations = page.items,
                        hasMore = page.hasMore,
                        nextCursorUpdatedAt = page.nextCursorUpdatedAt,
                        nextCursorId = page.nextCursorId,
                        isLoadingInitial = false,
                        isConnected = true
                    )
                }
            }
        }
    }

    fun loadMoreConversations() {
        val current = _uiState.value
        if (!current.hasMore || current.isLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val page = bridgeClient.fetchConversationsPage(
                beforeUpdatedAt = current.nextCursorUpdatedAt,
                beforeId = current.nextCursorId,
                pageSize = WearBridgeConstants.DEFAULT_CONVERSATIONS_PAGE_SIZE
            )
            if (page != null) {
                _uiState.update { state ->
                    val merged = (state.conversations + page.items).distinctBy { it.id }
                    state.copy(
                        conversations = merged,
                        hasMore = page.hasMore,
                        nextCursorUpdatedAt = page.nextCursorUpdatedAt,
                        nextCursorId = page.nextCursorId,
                        isLoadingMore = false,
                        isConnected = true
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun requestDeleteConversation(item: WearConversationItem) {
        _uiState.update { it.copy(pendingDeleteConversation = item) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(pendingDeleteConversation = null) }
    }

    fun confirmDeleteConversation() {
        val target = _uiState.value.pendingDeleteConversation ?: return
        _uiState.update { it.copy(pendingDeleteConversation = null) }
        viewModelScope.launch {
            val result = bridgeClient.deleteConversation(target.id)
            if (result.success) {
                _uiState.update { state ->
                    state.copy(
                        conversations = state.conversations.filterNot { it.id == target.id }
                    )
                }
            }
        }
    }

    fun showNoPresetNotice() {
        _uiState.update { it.copy(showNoPresetNotice = true) }
    }

    fun dismissNoPresetNotice() {
        _uiState.update { it.copy(showNoPresetNotice = false) }
    }
}
