package com.iank.im.peek.ui.main

import com.iank.im.peek.domain.model.ImageResolutionPreset

sealed interface OverlayState {
    data object None : OverlayState
    data object Settings : OverlayState
    data object PresetMenu : OverlayState
    data class MessageMenu(val messageId: String) : OverlayState
    data class AttachmentMenu(val attachmentId: String, val messageId: String) : OverlayState
    data class TextSelection(val text: String) : OverlayState
    data class EditMessage(
        val messageId: String,
        val initialText: String,
        val isUserMessage: Boolean
    ) : OverlayState

    data class ReportMessage(val messageId: String) : OverlayState

    data class ResolutionPicker(
        val attachmentId: String,
        val currentEffectivePreset: ImageResolutionPreset
    ) : OverlayState

    data class ResolutionConfirmScope(
        val attachmentId: String,
        val selectedPreset: ImageResolutionPreset
    ) : OverlayState

    data class DeleteConversationConfirm(
        val conversationId: String,
        val title: String
    ) : OverlayState

    data class ImageViewer(
        val attachmentId: String,
        val originalRelPath: String
    ) : OverlayState
}
