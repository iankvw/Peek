package com.iank.im.peek.ui.main

import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.AppSettings
import com.iank.im.peek.domain.model.CameraLensOption
import com.iank.im.peek.domain.model.ChatMessage
import com.iank.im.peek.domain.model.Conversation
import com.iank.im.peek.domain.model.PendingAttachment
import com.iank.im.peek.ui.common.UiText

enum class ScreenMode {
    CAMERA_EXPLORE,
    CHAT_ACTIVE
}

data class MainUiState(
    val screenMode: ScreenMode = ScreenMode.CAMERA_EXPLORE,
    val activeConversationId: String? = null,
    val activeConversation: Conversation? = null,
    val conversations: List<Conversation> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val inputText: String = "",
    val drawerSearchQuery: String = "",
    val presets: List<ApiPreset> = emptyList(),
    val selectedPreset: ApiPreset? = null,
    val settings: AppSettings = AppSettings(),
    val hasCameraPermission: Boolean = false,
    val activeCameraLens: CameraLensOption = CameraLensOption.BACK,
    val captureInFlight: Int = 0,
    val isSendingInActiveConversation: Boolean = false,
    val isResolutionUpdating: Boolean = false,
    val overlay: OverlayState = OverlayState.None,
    val storageUsageBytes: Long = 0L,
    val cameraErrorText: UiText? = null,
    val connectionTestStatus: ConnectionTestUiState = ConnectionTestUiState.Idle,
    val keystoreCorruptedNotice: Boolean = false
) {
    val cameraShouldRun: Boolean
        get() = hasCameraPermission &&
            (screenMode == ScreenMode.CAMERA_EXPLORE || captureInFlight > 0) &&
            overlay !is OverlayState.ImageViewer

    val canSend: Boolean
        get() = captureInFlight == 0 &&
            !isResolutionUpdating &&
            (inputText.isNotBlank() || pendingAttachments.isNotEmpty())
}

sealed interface ConnectionTestUiState {
    data object Idle : ConnectionTestUiState
    data object Testing : ConnectionTestUiState
    data object Success : ConnectionTestUiState
    data class Failed(val message: UiText) : ConnectionTestUiState
}

sealed interface UiEvent {
    data class ShowSnackbar(
        val message: UiText,
        val isUndoPendingClear: Boolean = false
    ) : UiEvent

    data object HideKeyboardAndClearFocus : UiEvent
    data object CloseDrawer : UiEvent
    data class CopyImageUriToClipboard(val uriString: String) : UiEvent
}
