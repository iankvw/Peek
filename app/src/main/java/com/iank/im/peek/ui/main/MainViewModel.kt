package com.iank.im.peek.ui.main

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iank.im.peek.R
import com.iank.im.peek.data.local.image.InternalImageStorage
import com.iank.im.peek.data.local.image.StageImageResult
import com.iank.im.peek.data.local.maintenance.StartupMaintenance
import com.iank.im.peek.domain.model.ApiException
import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.AppLanguage
import com.iank.im.peek.domain.model.AppSettings
import com.iank.im.peek.domain.model.CameraLensOption
import com.iank.im.peek.domain.model.CaptureResolutionOption
import com.iank.im.peek.domain.model.ContextRetentionMode
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.model.ImageSendMethod
import com.iank.im.peek.domain.model.MessageAttachment
import com.iank.im.peek.domain.model.OpenAiImageDetail
import com.iank.im.peek.domain.model.PendingAttachment
import com.iank.im.peek.domain.repository.ConversationRepository
import com.iank.im.peek.domain.repository.SettingsRepository
import com.iank.im.peek.domain.repository.VisionAiRepository
import com.iank.im.peek.domain.usecase.ActiveSendRegistry
import com.iank.im.peek.domain.usecase.CommitAttachmentsUseCase
import com.iank.im.peek.domain.usecase.DeleteConversationUseCase
import com.iank.im.peek.domain.usecase.EditMessageUseCase
import com.iank.im.peek.domain.usecase.LocalizedResourceProvider
import com.iank.im.peek.domain.usecase.ReprocessAttachmentResolutionUseCase
import com.iank.im.peek.domain.usecase.SendChatMessageUseCase
import com.iank.im.peek.domain.usecase.StageAttachmentUseCase
import com.iank.im.peek.ui.common.AppLocaleManager
import com.iank.im.peek.ui.common.ErrorUiMapper
import com.iank.im.peek.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val visionAiRepository: VisionAiRepository,
    private val stageAttachmentUseCase: StageAttachmentUseCase,
    private val commitAttachmentsUseCase: CommitAttachmentsUseCase,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val editMessageUseCase: EditMessageUseCase,
    private val deleteConversationUseCase: DeleteConversationUseCase,
    private val reprocessResolutionUseCase: ReprocessAttachmentResolutionUseCase,
    private val activeSendRegistry: ActiveSendRegistry,
    private val imageStorage: InternalImageStorage,
    private val startupMaintenance: StartupMaintenance,
    private val resources: LocalizedResourceProvider,
    private val json: Json
) : ViewModel() {

    private val screenModeFlow = MutableStateFlow(restoreScreenMode())
    private val activeConversationIdFlow = MutableStateFlow(restoreActiveConversationId())
    private val pendingAttachmentsFlow = MutableStateFlow(restorePendingAttachments())
    private val inputTextFlow = MutableStateFlow("")
    private val drawerSearchQueryFlow = MutableStateFlow("")
    private val hasCameraPermissionFlow = MutableStateFlow(false)
    private val activeCameraLensFlow = MutableStateFlow(CameraLensOption.BACK)
    private val lensInitializedFromSettings = MutableStateFlow(false)
    private val captureInFlightFlow = MutableStateFlow(0)
    private val overlayFlow = MutableStateFlow<OverlayState>(OverlayState.None)
    private val storageUsageBytesFlow = MutableStateFlow(0L)
    private val cameraErrorTextFlow = MutableStateFlow<UiText?>(null)
    private val connectionTestStatusFlow = MutableStateFlow<ConnectionTestUiState>(ConnectionTestUiState.Idle)

    private var clearedPendingBackup: List<PendingAttachment> = emptyList()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val conversationsFlow = drawerSearchQueryFlow
        .flatMapLatest { query -> conversationRepository.observeConversations(query) }

    private val activeConversationFlow = activeConversationIdFlow
        .flatMapLatest { convId ->
            if (convId == null) flowOf(null) else conversationRepository.observeConversation(convId)
        }

    private val activeMessagesFlow = activeConversationIdFlow
        .flatMapLatest { convId ->
            if (convId == null) flowOf(emptyList()) else conversationRepository.observeMessages(convId)
        }

    private data class CoreUiSlice(
        val screenMode: ScreenMode,
        val activeConversationId: String?,
        val activeConversation: com.iank.im.peek.domain.model.Conversation?,
        val conversations: List<com.iank.im.peek.domain.model.Conversation>,
        val messages: List<com.iank.im.peek.domain.model.ChatMessage>
    )

    private data class InputUiSlice(
        val pending: List<PendingAttachment>,
        val inputText: String,
        val searchQuery: String,
        val overlay: OverlayState,
        val storageBytes: Long
    )

    private data class HardwareUiSlice(
        val hasCameraPermission: Boolean,
        val lens: CameraLensOption,
        val captureInFlight: Int,
        val isSendingActive: Boolean,
        val isResolutionUpdating: Boolean,
        val cameraError: UiText?,
        val connectionTestStatus: ConnectionTestUiState,
        val keystoreCorrupted: Boolean
    )

    private val coreSliceFlow = combine(
        screenModeFlow,
        activeConversationIdFlow,
        activeConversationFlow,
        conversationsFlow,
        activeMessagesFlow
    ) { mode, convId, activeConv, convs, msgs ->
        CoreUiSlice(mode, convId, activeConv, convs, msgs)
    }

    private val inputSliceFlow = combine(
        pendingAttachmentsFlow,
        inputTextFlow,
        drawerSearchQueryFlow,
        overlayFlow,
        storageUsageBytesFlow
    ) { pending, input, query, overlay, bytes ->
        InputUiSlice(pending, input, query, overlay, bytes)
    }

    private val isSendingInActiveFlow = combine(
        activeConversationIdFlow,
        activeSendRegistry.activeConversationIds
    ) { activeId, sendingIds ->
        activeId != null && activeId in sendingIds
    }

    private val hardwareSliceFlow = combine(
        combine(
            hasCameraPermissionFlow,
            activeCameraLensFlow,
            captureInFlightFlow,
            isSendingInActiveFlow,
            reprocessResolutionUseCase.isUpdatingResolution
        ) { hasPerm, lens, inFlight, isSending, isUpdatingRes ->
            listOf(hasPerm, lens, inFlight, isSending, isUpdatingRes)
        },
        combine(
            cameraErrorTextFlow,
            connectionTestStatusFlow,
            settingsRepository.keystoreCorruptedEvent
        ) { camErr, connTest, keystoreErr ->
            Triple(camErr, connTest, keystoreErr)
        }
    ) { flags, extras ->
        HardwareUiSlice(
            hasCameraPermission = flags[0] as Boolean,
            lens = flags[1] as CameraLensOption,
            captureInFlight = flags[2] as Int,
            isSendingActive = flags[3] as Boolean,
            isResolutionUpdating = flags[4] as Boolean,
            cameraError = extras.first,
            connectionTestStatus = extras.second,
            keystoreCorrupted = extras.third
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        coreSliceFlow,
        inputSliceFlow,
        hardwareSliceFlow,
        settingsRepository.presets,
        settingsRepository.selectedPreset,
        settingsRepository.settings
    ) { values ->
        val core = values[0] as CoreUiSlice
        val input = values[1] as InputUiSlice
        val hw = values[2] as HardwareUiSlice
        @Suppress("UNCHECKED_CAST")
        val presets = values[3] as List<ApiPreset>
        val selectedPreset = values[4] as ApiPreset?
        val settings = values[5] as AppSettings

        MainUiState(
            screenMode = core.screenMode,
            activeConversationId = core.activeConversationId,
            activeConversation = core.activeConversation,
            conversations = core.conversations,
            messages = core.messages,
            pendingAttachments = input.pending,
            inputText = input.inputText,
            drawerSearchQuery = input.searchQuery,
            presets = presets,
            selectedPreset = selectedPreset,
            settings = settings,
            hasCameraPermission = hw.hasCameraPermission,
            activeCameraLens = hw.lens,
            captureInFlight = hw.captureInFlight,
            isSendingInActiveConversation = hw.isSendingActive,
            isResolutionUpdating = hw.isResolutionUpdating,
            overlay = input.overlay,
            storageUsageBytes = input.storageBytes,
            cameraErrorText = hw.cameraError,
            connectionTestStatus = hw.connectionTestStatus,
            keystoreCorruptedNotice = hw.keystoreCorrupted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState()
    )

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                if (!lensInitializedFromSettings.value) {
                    activeCameraLensFlow.value = settings.defaultCameraLens
                    lensInitializedFromSettings.value = true
                }
            }
        }
        viewModelScope.launch {
            sendChatMessageUseCase.preflightNotices.collect { notice ->
                _events.tryEmit(UiEvent.ShowSnackbar(ErrorUiMapper.toUiText(notice)))
            }
        }
        refreshStorageUsage()
    }

    fun resolveImageFile(relPath: String?): File? = imageStorage.resolveIfExists(relPath)

    fun onCameraPermissionChanged(granted: Boolean) {
        hasCameraPermissionFlow.value = granted
        if (!granted) {
            cameraErrorTextFlow.value = null
        }
    }

    fun onCameraError(errorText: UiText?) {
        cameraErrorTextFlow.value = errorText
    }

    fun onCameraLensFallback(fallbackLens: CameraLensOption) {
        activeCameraLensFlow.value = fallbackLens
        val lensNameRes = if (fallbackLens == CameraLensOption.BACK) {
            R.string.camera_lens_back
        } else {
            R.string.camera_lens_front
        }
        val localizedLens = resources.getString(lensNameRes)
        _events.tryEmit(
            UiEvent.ShowSnackbar(
                UiText.StringResource(R.string.camera_lens_fallback_notice, localizedLens)
            )
        )
    }

    fun toggleCameraLens() {
        if (captureInFlightFlow.value > 0) return
        activeCameraLensFlow.value = activeCameraLensFlow.value.toggled()
        cameraErrorTextFlow.value = null
    }

    fun prepareStagingCaptureTarget(): Pair<String, File> {
        imageStorage.ensureDirectories()
        val id = UUID.randomUUID().toString()
        val relPath = imageStorage.createStagingOriginalRelPath(id, "jpg")
        val file = imageStorage.resolve(relPath)
        file.parentFile?.mkdirs()
        return id to file
    }

    fun onCaptureStarted() {
        captureInFlightFlow.value = captureInFlightFlow.value + 1
    }

    fun onCaptureSucceeded(id: String, stagingRelPath: String) {
        viewModelScope.launch {
            try {
                when (val result = stageAttachmentUseCase.stageFromCameraFile(stagingRelPath, id)) {
                    is StageImageResult.Success -> {
                        appendPendingAttachment(
                            PendingAttachment(
                                id = result.id,
                                stagingOriginalRelPath = result.stagingOriginalRelPath,
                                stagingThumbnailRelPath = result.stagingThumbnailRelPath,
                                mimeType = result.mimeType,
                                width = result.width,
                                height = result.height
                            )
                        )
                    }
                    else -> {
                        imageStorage.deleteRelPath(stagingRelPath)
                        _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.capture_failed)))
                    }
                }
            } finally {
                captureInFlightFlow.value = (captureInFlightFlow.value - 1).coerceAtLeast(0)
            }
        }
    }

    fun onCaptureFailed(stagingRelPath: String, cancelledByGate: Boolean) {
        captureInFlightFlow.value = (captureInFlightFlow.value - 1).coerceAtLeast(0)
        viewModelScope.launch(Dispatchers.IO) {
            imageStorage.deleteRelPath(stagingRelPath)
        }
        if (!cancelledByGate) {
            _events.tryEmit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.capture_failed)))
        }
    }

    fun onGalleryUrisSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            for (uri in uris) {
                when (val result = stageAttachmentUseCase.stageFromGalleryUri(uri)) {
                    is StageImageResult.Success -> {
                        appendPendingAttachment(
                            PendingAttachment(
                                id = result.id,
                                stagingOriginalRelPath = result.stagingOriginalRelPath,
                                stagingThumbnailRelPath = result.stagingThumbnailRelPath,
                                mimeType = result.mimeType,
                                width = result.width,
                                height = result.height
                            )
                        )
                    }
                    StageImageResult.FileTooLarge -> {
                        _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.gallery_file_too_large)))
                    }
                    StageImageResult.UnsupportedFormat -> {
                        _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.gallery_unsupported_format)))
                    }
                    is StageImageResult.Failed -> {
                        _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.gallery_import_failed)))
                    }
                }
            }
        }
    }

    fun onReplaceAttachmentUriSelected(attachmentId: String, uri: Uri) {
        viewModelScope.launch {
            when (val result = stageAttachmentUseCase.stageFromGalleryUri(uri)) {
                is StageImageResult.Success -> {
                    val pending = PendingAttachment(
                        id = result.id,
                        stagingOriginalRelPath = result.stagingOriginalRelPath,
                        stagingThumbnailRelPath = result.stagingThumbnailRelPath,
                        mimeType = result.mimeType,
                        width = result.width,
                        height = result.height
                    )
                    val committed = withContext(Dispatchers.IO) {
                        commitAttachmentsUseCase.commitStaged(listOf(pending)).firstOrNull()
                    }
                    if (committed != null) {
                        conversationRepository.replaceAttachment(
                            attachmentId = attachmentId,
                            newOriginalRelPath = committed.originalRelPath,
                            newMimeType = committed.originalMimeType,
                            newWidth = committed.originalWidth,
                            newHeight = committed.originalHeight,
                            newThumbnailRelPath = committed.thumbnailRelPath
                        )
                    }
                }
                StageImageResult.FileTooLarge -> {
                    _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.gallery_file_too_large)))
                }
                StageImageResult.UnsupportedFormat -> {
                    _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.gallery_unsupported_format)))
                }
                is StageImageResult.Failed -> {
                    _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.gallery_import_failed)))
                }
            }
        }
    }

    fun removePendingAttachment(attachmentId: String) {
        val current = pendingAttachmentsFlow.value
        val target = current.firstOrNull { it.id == attachmentId } ?: return
        val updated = current.filterNot { it.id == attachmentId }
        updatePendingAttachments(updated)
        viewModelScope.launch(Dispatchers.IO) {
            stageAttachmentUseCase.removePendingAttachment(target)
        }
    }

    fun clearPendingAttachmentsWithUndo() {
        val current = pendingAttachmentsFlow.value
        if (current.isEmpty()) return
        clearedPendingBackup.takeIf { it.isNotEmpty() }?.let { oldBackup ->
            viewModelScope.launch(Dispatchers.IO) {
                stageAttachmentUseCase.removePendingList(oldBackup)
            }
        }
        clearedPendingBackup = current
        updatePendingAttachments(emptyList())
        _events.tryEmit(
            UiEvent.ShowSnackbar(
                message = UiText.StringResource(R.string.pending_attachments_cleared),
                isUndoPendingClear = true
            )
        )
    }

    fun undoClearPendingAttachments() {
        if (clearedPendingBackup.isEmpty()) return
        val restored = pendingAttachmentsFlow.value + clearedPendingBackup
        clearedPendingBackup = emptyList()
        updatePendingAttachments(restored)
    }

    fun finalizeClearedPendingBackup() {
        val backup = clearedPendingBackup
        if (backup.isEmpty()) return
        clearedPendingBackup = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            stageAttachmentUseCase.removePendingList(backup)
        }
    }

    fun updateInputText(text: String) {
        inputTextFlow.value = text
    }

    fun updateDrawerSearchQuery(query: String) {
        drawerSearchQueryFlow.value = query
    }

    fun setOverlay(overlay: OverlayState) {
        if (overlay !is OverlayState.None) {
            _events.tryEmit(UiEvent.HideKeyboardAndClearFocus)
        }
        if (overlay is OverlayState.Settings) {
            refreshStorageUsage()
            connectionTestStatusFlow.value = ConnectionTestUiState.Idle
        }
        overlayFlow.value = overlay
    }

    fun dismissOverlay() {
        overlayFlow.value = OverlayState.None
    }

    fun navigateToCameraExplore(keepConversation: Boolean = true) {
        _events.tryEmit(UiEvent.HideKeyboardAndClearFocus)
        overlayFlow.value = OverlayState.None
        if (!keepConversation) {
            setActiveConversationId(null)
        }
        setScreenMode(ScreenMode.CAMERA_EXPLORE)
    }

    fun detachContinuingConversation() {
        setActiveConversationId(null)
    }

    fun startNewConversationFromDrawer() {
        _events.tryEmit(UiEvent.CloseDrawer)
        _events.tryEmit(UiEvent.HideKeyboardAndClearFocus)
        overlayFlow.value = OverlayState.None
        setActiveConversationId(null)
        setScreenMode(ScreenMode.CAMERA_EXPLORE)
    }

    fun openConversationFromDrawer(conversationId: String) {
        _events.tryEmit(UiEvent.CloseDrawer)
        _events.tryEmit(UiEvent.HideKeyboardAndClearFocus)
        overlayFlow.value = OverlayState.None
        setActiveConversationId(conversationId)
        setScreenMode(ScreenMode.CHAT_ACTIVE)
    }

    fun sendMessage() {
        viewModelScope.launch {
            val state = uiState.value
            if (state.captureInFlight > 0) {
                _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.capture_in_flight_hint)))
                return@launch
            }
            if (state.isResolutionUpdating) {
                _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.validation_resolution_updating)))
                return@launch
            }
            val preset = state.selectedPreset
            if (preset == null) {
                _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.validation_no_preset)))
                setOverlay(OverlayState.Settings)
                return@launch
            }
            if (preset.modelId.isBlank()) {
                _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.validation_missing_model)))
                setOverlay(OverlayState.Settings)
                return@launch
            }
            val apiKey = settingsRepository.getApiKey(preset.id).orEmpty()
            val keyRequired = !(preset.provider == com.iank.im.peek.domain.model.ApiProvider.OPENAI &&
                preset.customBaseUrlEnabled)
            if (keyRequired && apiKey.isBlank()) {
                _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.validation_missing_api_key)))
                setOverlay(OverlayState.Settings)
                return@launch
            }
            if (state.inputText.isBlank() && state.pendingAttachments.isEmpty()) {
                _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.validation_empty_send)))
                return@launch
            }

            val wasExploring = state.screenMode == ScreenMode.CAMERA_EXPLORE
            if (wasExploring) {
                _events.emit(UiEvent.HideKeyboardAndClearFocus)
            }

            overlayFlow.value = OverlayState.None
            val textToSend = state.inputText
            val pendingToCommit = state.pendingAttachments

            inputTextFlow.value = ""
            updatePendingAttachments(emptyList())
            clearedPendingBackup = emptyList()

            setScreenMode(ScreenMode.CHAT_ACTIVE)

            val resolvedId = sendChatMessageUseCase.sendNewTurn(
                conversationId = state.activeConversationId,
                userText = textToSend,
                pendingAttachments = pendingToCommit,
                preset = preset,
                apiKey = apiKey
            )
            setActiveConversationId(resolvedId)
            refreshStorageUsage()
        }
    }

    fun retryAssistantMessage(assistantMessageId: String) {
        viewModelScope.launch {
            val preset = uiState.value.selectedPreset
            if (preset == null) {
                _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.validation_no_preset)))
                return@launch
            }
            val apiKey = settingsRepository.getApiKey(preset.id).orEmpty()
            val keyRequired = !(preset.provider == com.iank.im.peek.domain.model.ApiProvider.OPENAI &&
                preset.customBaseUrlEnabled)
            if (keyRequired && apiKey.isBlank()) {
                _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.validation_missing_api_key)))
                return@launch
            }
            sendChatMessageUseCase.retryAssistantMessage(
                assistantMessageId = assistantMessageId,
                preset = preset,
                apiKey = apiKey
            )
        }
    }

    fun stopActiveRequest() {
        val convId = activeConversationIdFlow.value ?: return
        sendChatMessageUseCase.cancelConversationRequest(convId)
    }

    fun editMessage(messageId: String, newText: String, isUserMessage: Boolean) {
        viewModelScope.launch {
            overlayFlow.value = OverlayState.None
            if (!isUserMessage) {
                editMessageUseCase(messageId, newText, null, null)
                return@launch
            }
            val preset = uiState.value.selectedPreset
            if (preset == null) {
                _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.validation_no_preset)))
                return@launch
            }
            val apiKey = settingsRepository.getApiKey(preset.id).orEmpty()
            val keyRequired = !(preset.provider == com.iank.im.peek.domain.model.ApiProvider.OPENAI &&
                preset.customBaseUrlEnabled)
            if (keyRequired && apiKey.isBlank()) {
                _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.validation_missing_api_key)))
                return@launch
            }
            editMessageUseCase(messageId, newText, preset, apiKey)
        }
    }

    fun toggleAttachmentTextReplaced(attachmentId: String) {
        viewModelScope.launch {
            overlayFlow.value = OverlayState.None
            conversationRepository.toggleAttachmentTextReplaced(attachmentId)
        }
    }

    fun startResolutionPicker(attachment: MessageAttachment) {
        val currentEffective = ImageResolutionPreset.resolveEffectivePreset(
            attachmentPreset = attachment.requestedPreset,
            conversationOverride = uiState.value.activeConversation?.overrideResolutionPreset,
            globalDefault = uiState.value.settings.defaultResolutionPreset
        )
        overlayFlow.value = OverlayState.ResolutionPicker(
            attachmentId = attachment.id,
            currentEffectivePreset = currentEffective
        )
    }

    fun onResolutionOptionChosen(attachmentId: String, preset: ImageResolutionPreset) {
        overlayFlow.value = OverlayState.ResolutionConfirmScope(
            attachmentId = attachmentId,
            selectedPreset = preset
        )
    }

    fun applyResolutionChoice(
        attachmentId: String,
        preset: ImageResolutionPreset,
        applyToEntireConversation: Boolean
    ) {
        viewModelScope.launch {
            overlayFlow.value = OverlayState.None
            val convId = activeConversationIdFlow.value
            val localizedLabel = resources.getString(resolutionLabelRes(preset))
            if (applyToEntireConversation && convId != null) {
                reprocessResolutionUseCase.applyToConversation(convId, preset)
                _events.emit(
                    UiEvent.ShowSnackbar(
                        UiText.StringResource(
                            R.string.resolution_updated_conversation,
                            localizedLabel
                        )
                    )
                )
            } else {
                reprocessResolutionUseCase.applyToSingleAttachment(attachmentId, preset)
                _events.emit(
                    UiEvent.ShowSnackbar(
                        UiText.StringResource(
                            R.string.resolution_updated_single,
                            localizedLabel
                        )
                    )
                )
            }
        }
    }

    private fun resolutionLabelRes(preset: ImageResolutionPreset): Int = when (preset) {
        ImageResolutionPreset.DEFAULT -> R.string.resolution_preset_default
        ImageResolutionPreset.ORIGINAL -> R.string.resolution_preset_original
        ImageResolutionPreset.HIGH_4000 -> R.string.resolution_preset_high_4000
        ImageResolutionPreset.STANDARD_2000 -> R.string.resolution_preset_standard_2000
        ImageResolutionPreset.SAVER_1000 -> R.string.resolution_preset_saver_1000
    }

    fun confirmDeleteConversation(conversationId: String) {
        viewModelScope.launch {
            overlayFlow.value = OverlayState.None
            if (activeConversationIdFlow.value == conversationId) {
                setActiveConversationId(null)
                if (screenModeFlow.value == ScreenMode.CHAT_ACTIVE) {
                    setScreenMode(ScreenMode.CAMERA_EXPLORE)
                }
            }
            deleteConversationUseCase(conversationId)
            refreshStorageUsage()
        }
    }

    fun copyAttachmentImageToClipboard(attachmentId: String) {
        viewModelScope.launch {
            overlayFlow.value = OverlayState.None
            val att = conversationRepository.getAttachment(attachmentId) ?: return@launch
            val relPath = att.payloadRelPath?.takeIf { imageStorage.resolveIfExists(it) != null }
                ?: att.originalRelPath
            val mime = if (relPath == att.payloadRelPath) {
                uiState.value.selectedPreset?.imagePayloadFormat?.mimeType ?: "image/webp"
            } else {
                att.originalMimeType
            }
            val uri = withContext(Dispatchers.IO) {
                imageStorage.copyToShareCacheAndGetUri(relPath, mime)
            }
            if (uri != null) {
                _events.emit(UiEvent.CopyImageUriToClipboard(uri.toString()))
                _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.chat_image_copied_to_clipboard)))
            } else {
                _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.chat_image_copy_failed)))
            }
        }
    }

    fun selectPreset(presetId: String) {
        viewModelScope.launch {
            settingsRepository.selectPreset(presetId)
            if (overlayFlow.value is OverlayState.PresetMenu) {
                overlayFlow.value = OverlayState.None
            }
        }
    }

    suspend fun loadApiKeyForPreset(presetId: String): String =
        settingsRepository.getApiKey(presetId).orEmpty()

    fun savePreset(preset: ApiPreset, apiKey: String) {
        viewModelScope.launch {
            settingsRepository.upsertPreset(preset, apiKey)
            settingsRepository.selectPreset(preset.id)
            _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.settings_preset_saved)))
        }
    }

    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            settingsRepository.deletePreset(presetId)
            _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.settings_preset_deleted)))
        }
    }

    fun testPresetConnection(preset: ApiPreset, apiKey: String) {
        viewModelScope.launch {
            connectionTestStatusFlow.value = ConnectionTestUiState.Testing
            val result = visionAiRepository.testConnection(preset, apiKey)
            connectionTestStatusFlow.value = result.fold(
                onSuccess = { ConnectionTestUiState.Success },
                onFailure = { throwable ->
                    val apiErr = (throwable as? ApiException)?.error
                    val uiText = if (apiErr != null) {
                        ErrorUiMapper.toUiText(apiErr)
                    } else {
                        UiText.Dynamic(throwable.message.orEmpty())
                    }
                    ConnectionTestUiState.Failed(uiText)
                }
            )
        }
    }

    fun resetConnectionTestStatus() {
        connectionTestStatusFlow.value = ConnectionTestUiState.Idle
    }

    fun updateImageSendMethod(method: ImageSendMethod) {
        viewModelScope.launch { settingsRepository.updateImageSendMethod(method) }
    }

    fun updateDefaultResolutionPreset(preset: ImageResolutionPreset) {
        viewModelScope.launch { settingsRepository.updateDefaultResolutionPreset(preset) }
    }

    fun updateContextRetentionMode(mode: ContextRetentionMode) {
        viewModelScope.launch { settingsRepository.updateContextRetentionMode(mode) }
    }

    fun updateAutoTextReplaceOldImages(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoTextReplaceOldImages(enabled) }
    }

    fun updateKeepRecentImageTurns(turns: Int) {
        viewModelScope.launch { settingsRepository.updateKeepRecentImageTurns(turns) }
    }

    fun updateBackgroundCaptionEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateBackgroundCaptionEnabled(enabled) }
    }

    fun updateCaptionModelId(modelId: String) {
        viewModelScope.launch { settingsRepository.updateCaptionModelId(modelId) }
    }

    fun updateDefaultCameraLens(lens: CameraLensOption) {
        viewModelScope.launch {
            settingsRepository.updateDefaultCameraLens(lens)
            activeCameraLensFlow.value = lens
        }
    }

    fun updateCaptureResolution(resolution: CaptureResolutionOption) {
        viewModelScope.launch { settingsRepository.updateCaptureResolution(resolution) }
    }

    fun updateAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.updateAppLanguage(language)
            AppLocaleManager.applyLanguage(language)
        }
    }

    fun updateSystemPrompt(prompt: String) {
        viewModelScope.launch { settingsRepository.updateSystemPrompt(prompt) }
    }

    fun updateOpenAiImageDetail(detail: OpenAiImageDetail) {
        viewModelScope.launch { settingsRepository.updateOpenAiImageDetail(detail) }
    }

    fun updateOpenAiUseDeveloperRole(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateOpenAiUseDeveloperRole(enabled) }
    }

    fun acknowledgeKeystoreCorrupted() {
        settingsRepository.let {
            viewModelScope.launch { it.acknowledgeKeystoreCorrupted() }
        }
    }

    fun runManualStorageCleanup() {
        viewModelScope.launch {
            startupMaintenance.runManualMaintenance()
            refreshStorageUsage()
            _events.emit(UiEvent.ShowSnackbar(UiText.StringResource(R.string.settings_cleanup_done)))
        }
    }

    fun notifyMessage(uiText: UiText) {
        _events.tryEmit(UiEvent.ShowSnackbar(uiText))
    }

    private fun refreshStorageUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            storageUsageBytesFlow.value = imageStorage.calculateTotalImageStorageBytes()
        }
    }

    private fun appendPendingAttachment(item: PendingAttachment) {
        updatePendingAttachments(pendingAttachmentsFlow.value + item)
    }

    private fun updatePendingAttachments(list: List<PendingAttachment>) {
        pendingAttachmentsFlow.value = list
        savedStateHandle[KEY_PENDING_ATTACHMENTS] =
            json.encodeToString(ListSerializer(PendingAttachment.serializer()), list)
    }

    private fun setScreenMode(mode: ScreenMode) {
        screenModeFlow.value = mode
        savedStateHandle[KEY_SCREEN_MODE] = mode.name
    }

    private fun setActiveConversationId(id: String?) {
        activeConversationIdFlow.value = id
        savedStateHandle[KEY_ACTIVE_CONVERSATION_ID] = id
    }

    private fun restoreScreenMode(): ScreenMode {
        val raw = savedStateHandle.get<String>(KEY_SCREEN_MODE)
        return ScreenMode.entries.firstOrNull { it.name == raw } ?: ScreenMode.CAMERA_EXPLORE
    }

    private fun restoreActiveConversationId(): String? =
        savedStateHandle.get<String>(KEY_ACTIVE_CONVERSATION_ID)

    private fun restorePendingAttachments(): List<PendingAttachment> {
        val raw = savedStateHandle.get<String>(KEY_PENDING_ATTACHMENTS) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(PendingAttachment.serializer()), raw)
                .filter { imageStorage.resolveIfExists(it.stagingOriginalRelPath) != null }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_SCREEN_MODE = "screen_mode"
        private const val KEY_ACTIVE_CONVERSATION_ID = "active_conversation_id"
        private const val KEY_PENDING_ATTACHMENTS = "pending_attachments"
    }
}
