package com.iank.im.peek.ui.main

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iank.im.peek.R
import com.iank.im.peek.ui.camera.BottomCaptureAndInputBar
import com.iank.im.peek.ui.camera.CameraPreviewLayer
import com.iank.im.peek.ui.camera.CameraSessionController
import com.iank.im.peek.ui.camera.TopControlBar
import com.iank.im.peek.ui.chat.AttachmentContextMenuDialog
import com.iank.im.peek.ui.chat.ChatPanel
import com.iank.im.peek.ui.chat.EditMessageDialog
import com.iank.im.peek.ui.chat.FullScreenImageViewerDialog
import com.iank.im.peek.ui.chat.MessageContextMenuDialog
import com.iank.im.peek.ui.chat.ResolutionConfirmScopeDialog
import com.iank.im.peek.ui.chat.ResolutionPickerDialog
import com.iank.im.peek.ui.chat.TextSelectionDialog
import com.iank.im.peek.ui.common.UiText
import com.iank.im.peek.ui.drawer.DeleteConversationDialog
import com.iank.im.peek.ui.drawer.NavigationDrawerContent
import com.iank.im.peek.ui.settings.SettingsBottomSheet
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    val cameraController = remember(context) { CameraSessionController(context) }
    DisposableEffect(cameraController) {
        onDispose {
            cameraController.dispose()
        }
    }

    var requestedPermissionOnce by rememberSaveable { mutableStateOf(false) }
    var replaceTargetAttachmentId by rememberSaveable { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCameraPermissionChanged(granted)
    }

    val multiGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        viewModel.onGalleryUrisSelected(uris)
    }

    val replacePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val targetId = replaceTargetAttachmentId
        replaceTargetAttachmentId = null
        if (targetId != null && uri != null) {
            viewModel.onReplaceAttachmentUriSelected(targetId, uri)
        }
    }

    fun checkAndSyncCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onCameraPermissionChanged(granted)
        if (!granted && !requestedPermissionOnce) {
            requestedPermissionOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(lifecycleOwner) {
        checkAndSyncCameraPermission()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                viewModel.onCameraPermissionChanged(granted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }

    fun copyPlainText(text: String, feedbackRes: Int = R.string.chat_copied_to_clipboard) {
        clipboardManager?.setPrimaryClip(ClipData.newPlainText("peek_text", text))
        viewModel.notifyMessage(UiText.StringResource(feedbackRes))
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    val text = event.message.asString(context)
                    val undoLabel = if (event.isUndoPendingClear) {
                        context.getString(R.string.action_undo)
                    } else {
                        null
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = text,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short
                    )
                    if (event.isUndoPendingClear) {
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoClearPendingAttachments()
                        } else {
                            viewModel.finalizeClearedPendingBackup()
                        }
                    }
                }
                UiEvent.HideKeyboardAndClearFocus -> {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
                UiEvent.CloseDrawer -> {
                    if (drawerState.isOpen) {
                        drawerState.close()
                    }
                }
                is UiEvent.CopyImageUriToClipboard -> {
                    val uri = Uri.parse(event.uriString)
                    clipboardManager?.setPrimaryClip(
                        ClipData.newUri(context.contentResolver, "peek_image", uri)
                    )
                }
            }
        }
    }

    fun openGalleryPicker() {
        focusManager.clearFocus()
        keyboardController?.hide()
        multiGalleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun openAppPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            NavigationDrawerContent(
                drawerState = drawerState,
                isOpen = drawerState.isOpen,
                conversations = uiState.conversations,
                activeConversationId = uiState.activeConversationId,
                searchQuery = uiState.drawerSearchQuery,
                onSearchQueryChanged = viewModel::updateDrawerSearchQuery,
                onNewConversation = viewModel::startNewConversationFromDrawer,
                onSelectConversation = viewModel::openConversationFromDrawer,
                onRequestDeleteConversation = { conv ->
                    viewModel.setOverlay(
                        OverlayState.DeleteConversationConfirm(
                            conversationId = conv.id,
                            title = conv.title
                        )
                    )
                },
                onOpenSettings = {
                    coroutineScope.launch {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        drawerState.close()
                        viewModel.setOverlay(OverlayState.Settings)
                    }
                }
            )
        }
    ) {
        // LIFO BackHandler priority (5 & 6): only active when drawer is closed and no overlay is open
        BackHandler(
            enabled = uiState.screenMode == ScreenMode.CHAT_ACTIVE &&
                !drawerState.isOpen &&
                uiState.overlay == OverlayState.None
        ) {
            viewModel.navigateToCameraExplore(keepConversation = true)
        }

        BackHandler(
            enabled = uiState.screenMode == ScreenMode.CAMERA_EXPLORE &&
                uiState.pendingAttachments.isNotEmpty() &&
                !drawerState.isOpen &&
                uiState.overlay == OverlayState.None
        ) {
            viewModel.clearPendingAttachmentsWithUndo()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreviewLayer(
                controller = cameraController,
                hasCameraPermission = uiState.hasCameraPermission,
                cameraShouldRun = uiState.cameraShouldRun,
                activeLens = uiState.activeCameraLens,
                captureResolution = uiState.settings.captureResolution,
                cameraErrorText = uiState.cameraErrorText,
                onLensFallback = viewModel::onCameraLensFallback,
                onCameraError = viewModel::onCameraError,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenAppSettings = ::openAppPermissionSettings
            )

            // Explore overlay controls
            if (uiState.screenMode == ScreenMode.CAMERA_EXPLORE) {
                Box(modifier = Modifier.fillMaxSize()) {
                    TopControlBar(
                        selectableTargets = uiState.selectableTargets,
                        activeTarget = uiState.activeTarget,
                        activeConversation = uiState.activeConversation,
                        activeLens = uiState.activeCameraLens,
                        isPresetMenuOpen = uiState.overlay is OverlayState.PresetMenu,
                        hasCameraPermission = uiState.hasCameraPermission,
                        captureInFlight = uiState.captureInFlight,
                        onOpenDrawer = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            coroutineScope.launch { drawerState.open() }
                        },
                        onTogglePresetMenu = { open ->
                            viewModel.setOverlay(
                                if (open) OverlayState.PresetMenu else OverlayState.None
                            )
                        },
                        onSelectTarget = viewModel::selectTargetForCurrentContext,
                        onOpenSettings = { viewModel.setOverlay(OverlayState.Settings) },
                        onSelectLens = viewModel::selectCameraLens,
                        onSwitchLens = viewModel::toggleCameraLens,
                        onReturnToActiveChat = viewModel::returnToActiveConversationFromCamera,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    BottomCaptureAndInputBar(
                        pendingAttachments = uiState.pendingAttachments,
                        resolveFile = viewModel::resolveImageFile,
                        inputText = uiState.inputText,
                        hasCameraPermission = uiState.hasCameraPermission,
                        captureInFlight = uiState.captureInFlight,
                        canSend = uiState.canSend,
                        onInputChanged = viewModel::updateInputText,
                        onRemovePending = viewModel::removePendingAttachment,
                        onPickFromGallery = ::openGalleryPicker,
                        onCapturePhoto = {
                            cameraController.capturePhoto(
                                prepareTarget = viewModel::prepareStagingCaptureTarget,
                                onCaptureStarted = viewModel::onCaptureStarted,
                                onCaptureSucceeded = viewModel::onCaptureSucceeded,
                                onCaptureFailed = viewModel::onCaptureFailed
                            )
                        },
                        onSend = viewModel::sendMessage,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            // Full-screen opaque ChatPanel in the same window
            AnimatedVisibility(
                visible = uiState.screenMode == ScreenMode.CHAT_ACTIVE,
                enter = fadeIn() + slideInVertically { it / 10 },
                exit = fadeOut() + slideOutVertically { it / 10 },
                modifier = Modifier.fillMaxSize()
            ) {
                ChatPanel(
                    conversation = uiState.activeConversation,
                    messages = uiState.messages,
                    selectableTargets = uiState.selectableTargets,
                    activeTarget = uiState.activeTarget,
                    pendingAttachments = uiState.pendingAttachments,
                    inputText = uiState.inputText,
                    canSend = uiState.canSend,
                    isSendingInActiveConversation = uiState.isSendingInActiveConversation,
                    resolveFile = viewModel::resolveImageFile,
                    onOpenDrawer = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        coroutineScope.launch { drawerState.open() }
                    },
                    onReturnToCamera = {
                        viewModel.navigateToCameraExplore(keepConversation = true)
                    },
                    onNewConversation = viewModel::startNewConversationFromDrawer,
                    onSelectTarget = viewModel::selectTargetForCurrentContext,
                    onOpenSettings = { viewModel.setOverlay(OverlayState.Settings) },
                    onInputChanged = viewModel::updateInputText,
                    onRemovePending = viewModel::removePendingAttachment,
                    onPickFromGallery = ::openGalleryPicker,
                    onSend = viewModel::sendMessage,
                    onStop = viewModel::stopActiveRequest,
                    onRetryMessage = viewModel::retryAssistantMessage,
                    onTapAttachment = { attachment ->
                        viewModel.setOverlay(
                            OverlayState.ImageViewer(
                                attachmentId = attachment.id,
                                originalRelPath = attachment.originalRelPath
                            )
                        )
                    },
                    onLongPressAttachment = { attachment, messageId ->
                        viewModel.setOverlay(
                            OverlayState.AttachmentMenu(
                                attachmentId = attachment.id,
                                messageId = messageId
                            )
                        )
                    },
                    onLongPressMessage = { message ->
                        viewModel.setOverlay(OverlayState.MessageMenu(message.id))
                    },
                    onCopyCode = { code ->
                        copyPlainText(code, R.string.chat_code_copied)
                    }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 80.dp)
            )
        }
    }

    // Single modal overlay host
    when (val overlay = uiState.overlay) {
        OverlayState.None,
        OverlayState.PresetMenu -> Unit

        OverlayState.Settings -> {
            SettingsBottomSheet(
                presets = uiState.presets,
                modelGroups = uiState.modelGroups,
                selectedTargetId = uiState.globalSelectedTargetId,
                selectedPreset = uiState.selectedPreset,
                settings = uiState.settings,
                storageUsageBytes = uiState.storageUsageBytes,
                connectionTestStatus = uiState.connectionTestStatus,
                onDismiss = viewModel::dismissOverlay,
                onSelectPreset = viewModel::selectPreset,
                onLoadApiKey = viewModel::loadApiKeyForPreset,
                onSavePreset = viewModel::savePreset,
                onClonePreset = viewModel::clonePreset,
                onDeletePreset = viewModel::deletePreset,
                onSaveModelGroup = viewModel::saveModelGroup,
                onDeleteModelGroup = viewModel::deleteModelGroup,
                onTestConnection = viewModel::testPresetConnection,
                onResetConnectionTest = viewModel::resetConnectionTestStatus,
                onUpdateSendMethod = viewModel::updateImageSendMethod,
                onUpdateDefaultResolution = viewModel::updateDefaultResolutionPreset,
                onUpdateContextRetention = viewModel::updateContextRetentionMode,
                onUpdateAutoReplaceOldImages = viewModel::updateAutoTextReplaceOldImages,
                onUpdateKeepRecentImageTurns = viewModel::updateKeepRecentImageTurns,
                onUpdateOpenAiImageDetail = viewModel::updateOpenAiImageDetail,
                onUpdateOpenAiDeveloperRole = viewModel::updateOpenAiUseDeveloperRole,
                onUpdateBackgroundCaption = viewModel::updateBackgroundCaptionEnabled,
                onUpdateCaptionModelId = viewModel::updateCaptionModelId,
                onCleanupStorage = viewModel::runManualStorageCleanup,
                onUpdateDefaultLens = viewModel::updateDefaultCameraLens,
                onUpdateCaptureResolution = viewModel::updateCaptureResolution,
                onUpdateLanguage = viewModel::updateAppLanguage,
                onUpdateSystemPrompt = viewModel::updateSystemPrompt
            )
        }

        is OverlayState.MessageMenu -> {
            val targetMessage = uiState.messages.firstOrNull { it.id == overlay.messageId }
            if (targetMessage != null) {
                MessageContextMenuDialog(
                    message = targetMessage,
                    onCopyText = {
                        copyPlainText(targetMessage.textContent)
                        viewModel.dismissOverlay()
                    },
                    onSelectText = {
                        viewModel.setOverlay(OverlayState.TextSelection(targetMessage.textContent))
                    },
                    onEditMessage = {
                        viewModel.setOverlay(
                            OverlayState.EditMessage(
                                messageId = targetMessage.id,
                                initialText = targetMessage.textContent,
                                isUserMessage = targetMessage.role == com.iank.im.peek.domain.model.MessageRole.USER
                            )
                        )
                    },
                    onDismiss = viewModel::dismissOverlay
                )
            } else {
                LaunchedEffect(overlay) { viewModel.dismissOverlay() }
            }
        }

        is OverlayState.AttachmentMenu -> {
            val targetAttachment = uiState.messages
                .asSequence()
                .flatMap { it.attachments.asSequence() }
                .firstOrNull { it.id == overlay.attachmentId }
            if (targetAttachment != null) {
                AttachmentContextMenuDialog(
                    attachment = targetAttachment,
                    onCopyImage = {
                        viewModel.copyAttachmentImageToClipboard(targetAttachment.id)
                    },
                    onReplaceImage = {
                        viewModel.dismissOverlay()
                        replaceTargetAttachmentId = targetAttachment.id
                        replacePhotoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onToggleTextReplacement = {
                        viewModel.toggleAttachmentTextReplaced(targetAttachment.id)
                    },
                    onChangeResolution = {
                        viewModel.startResolutionPicker(targetAttachment)
                    },
                    onDismiss = viewModel::dismissOverlay
                )
            } else {
                LaunchedEffect(overlay) { viewModel.dismissOverlay() }
            }
        }

        is OverlayState.TextSelection -> {
            TextSelectionDialog(
                text = overlay.text,
                onDismiss = viewModel::dismissOverlay
            )
        }

        is OverlayState.EditMessage -> {
            EditMessageDialog(
                initialText = overlay.initialText,
                isUserMessage = overlay.isUserMessage,
                onConfirm = { newText ->
                    viewModel.editMessage(overlay.messageId, newText, overlay.isUserMessage)
                },
                onDismiss = viewModel::dismissOverlay
            )
        }

        is OverlayState.ResolutionPicker -> {
            ResolutionPickerDialog(
                currentEffectivePreset = overlay.currentEffectivePreset,
                onSelectOption = { chosen ->
                    viewModel.onResolutionOptionChosen(overlay.attachmentId, chosen)
                },
                onDismiss = viewModel::dismissOverlay
            )
        }

        is OverlayState.ResolutionConfirmScope -> {
            ResolutionConfirmScopeDialog(
                selectedPreset = overlay.selectedPreset,
                onApplyEntireConversation = {
                    viewModel.applyResolutionChoice(
                        attachmentId = overlay.attachmentId,
                        preset = overlay.selectedPreset,
                        applyToEntireConversation = true
                    )
                },
                onApplyThisPhotoOnly = {
                    viewModel.applyResolutionChoice(
                        attachmentId = overlay.attachmentId,
                        preset = overlay.selectedPreset,
                        applyToEntireConversation = false
                    )
                },
                onDismiss = viewModel::dismissOverlay
            )
        }

        is OverlayState.DeleteConversationConfirm -> {
            DeleteConversationDialog(
                title = overlay.title,
                onConfirm = { viewModel.confirmDeleteConversation(overlay.conversationId) },
                onDismiss = viewModel::dismissOverlay
            )
        }

        is OverlayState.ImageViewer -> {
            val file = viewModel.resolveImageFile(overlay.originalRelPath)
            FullScreenImageViewerDialog(
                imageFile = file,
                onDismiss = viewModel::dismissOverlay
            )
        }
    }

    if (uiState.keystoreCorruptedNotice) {
        AlertDialog(
            onDismissRequest = viewModel::acknowledgeKeystoreCorrupted,
            text = {
                Text(stringResource(R.string.error_keystore_corrupted_reenter_key))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.acknowledgeKeystoreCorrupted()
                        viewModel.setOverlay(OverlayState.Settings)
                    }
                ) {
                    Text(stringResource(R.string.action_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::acknowledgeKeystoreCorrupted) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}
