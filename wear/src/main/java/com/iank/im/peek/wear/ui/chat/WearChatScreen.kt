package com.iank.im.peek.wear.ui.chat

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material.scrollAway
import coil3.compose.AsyncImage
import com.iank.im.peek.wear.R
import com.iank.im.peek.wear.domain.model.WearAttachmentItem
import com.iank.im.peek.wear.domain.model.WearInputMode
import com.iank.im.peek.wear.domain.model.WearMessageItem
import com.iank.im.peek.wear.domain.model.WearMessageRole
import com.iank.im.peek.wear.domain.model.WearMessageStatus
import com.iank.im.peek.wear.ui.common.WearUiHelpers
import com.iank.im.peek.wear.ui.theme.PeekWearBackground
import com.iank.im.peek.wear.ui.theme.PeekWearError
import com.iank.im.peek.wear.ui.theme.PeekWearErrorContainer
import com.iank.im.peek.wear.ui.theme.PeekWearOnBackground
import com.iank.im.peek.wear.ui.theme.PeekWearOnPrimary
import com.iank.im.peek.wear.ui.theme.PeekWearOnPrimaryContainer
import com.iank.im.peek.wear.ui.theme.PeekWearOnSurfaceVariant
import com.iank.im.peek.wear.ui.theme.PeekWearOutlineVariant
import com.iank.im.peek.wear.ui.theme.PeekWearPrimary
import com.iank.im.peek.wear.ui.theme.PeekWearPrimaryContainer
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainer
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainerHigh
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.io.File

@Composable
fun WearChatScreen(
    submittedText: String? = null,
    onConsumeSubmittedText: () -> Unit = {},
    onOpenTextInput: () -> Unit,
    onOpenImageViewer: (String, Int) -> Unit,
    onOpenReportMessage: (String) -> Unit,
    viewModel: WearChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentInputMode by viewModel.defaultInputMethod.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberActiveFocusRequester()

    val voicePromptLabel = stringResource(R.string.chat_input_voice_prompt)

    LaunchedEffect(submittedText) {
        val trimmed = submittedText?.trim()
        if (!trimmed.isNullOrEmpty()) {
            onConsumeSubmittedText()
            viewModel.sendMessage(trimmed)
        }
    }

    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val text = WearInputLauncher.extractTextFromVoiceResult(result.data)
                if (!text.isNullOrBlank()) {
                    viewModel.sendMessage(text)
                }
            }
            Activity.RESULT_CANCELED -> {
                // User dismissed voice recognizer; stay safely on chat screen.
            }
            else -> {
                // Speech recognizer failed unexpectedly; fall back cleanly to in-app keyboard.
                onOpenTextInput()
            }
        }
    }

    val launchForMode by rememberUpdatedState { mode: WearInputMode ->
        when (mode) {
            WearInputMode.TEXT_KEYBOARD -> onOpenTextInput()
            WearInputMode.VOICE_FIRST -> {
                if (!WearInputLauncher.canLaunchVoiceRecognizer(context)) {
                    onOpenTextInput()
                } else {
                    val intent = WearInputLauncher.createVoiceRecognizerIntent(voicePromptLabel)
                    runCatching {
                        voiceInputLauncher.launch(intent)
                    }.onFailure {
                        onOpenTextInput()
                    }
                }
            }
        }
    }

    var hasCompletedInitialScroll by remember(uiState.conversationId) {
        mutableStateOf(uiState.conversationId == null)
    }

    // Auto-scroll to latest message + input bar on initial load or newly sent turn
    LaunchedEffect(uiState.scrollToBottomTick) {
        if (uiState.scrollToBottomTick > 0) {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) {
                listState.scrollToItem((total - 1).coerceAtLeast(0))
            }
            hasCompletedInitialScroll = true
        }
    }

    // Preserve scroll position when older messages are prepended at top
    LaunchedEffect(Unit) {
        viewModel.scrollEvents.collect { event ->
            when (event) {
                is WearChatScrollEvent.PrependedOlderMessages -> {
                    val targetIdx = (listState.centerItemIndex + event.addedCount).coerceAtLeast(0)
                    listState.scrollToItem(targetIdx, listState.centerItemScrollOffset)
                }
            }
        }
    }

    // Load older messages when scrolling near the top of the list (only after initial bottom scroll completes)
    LaunchedEffect(listState, uiState.hasOlder, hasCompletedInitialScroll) {
        if (!hasCompletedInitialScroll) return@LaunchedEffect
        snapshotFlow { listState.centerItemIndex }
            .distinctUntilChanged()
            .filter { idx ->
                idx <= 2 &&
                    uiState.hasOlder &&
                    !uiState.isLoadingOlder &&
                    !uiState.isLoadingInitial
            }
            .collect {
                viewModel.loadOlderMessages()
            }
    }

    Scaffold(
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(PeekWearBackground)
                .rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
                    focusRequester = focusRequester
                )
        ) {
            // Read-only bound model badge header
            item(key = "bound_target_header") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Text(
                        text = uiState.boundTargetDisplayName
                            ?: stringResource(R.string.chat_target_unconfigured),
                        style = MaterialTheme.typography.caption2,
                        color = PeekWearOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PeekWearSurfaceContainerHigh)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (uiState.isLoadingOlder) {
                item(key = "loading_older") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            indicatorColor = PeekWearPrimary,
                            trackColor = PeekWearSurfaceContainerHigh,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.chat_loading_older),
                            style = MaterialTheme.typography.caption2,
                            color = PeekWearOnSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.isLoadingInitial) {
                item(key = "loading_initial") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        CircularProgressIndicator(
                            indicatorColor = PeekWearPrimary,
                            trackColor = PeekWearSurfaceContainerHigh,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else if (uiState.messages.isEmpty()) {
                item(key = "empty_chat_hint") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(PeekWearSurfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.chat_empty_messages),
                            style = MaterialTheme.typography.body2,
                            color = PeekWearOnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(
                    items = uiState.messages,
                    key = { it.id }
                ) { message ->
                    WearChatMessageItem(
                        message = message,
                        thumbnailFiles = uiState.thumbnailFiles,
                        isReportedRevealed = message.id in uiState.revealedReportedIds,
                        onTapAttachment = onOpenImageViewer,
                        onRevealReported = { viewModel.toggleReportedMessageReveal(message.id) },
                        onLongPressAssistant = { viewModel.openMessageContextMenu(message) },
                        onRetry = { viewModel.retryMessage(message.id) },
                        onStop = viewModel::cancelGeneration
                    )
                }
            }

            // Transient error / validation banner if present
            val errorCode = uiState.errorBannerCode
            if (errorCode != null) {
                item(key = "error_banner") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(PeekWearErrorContainer)
                            .clickable(onClick = viewModel::dismissErrorBanner)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = WearUiHelpers.mapRejectionMessage(context, errorCode),
                            style = MaterialTheme.typography.caption1,
                            color = PeekWearError,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // AI Disclaimer strictly positioned below the last message and above input controls
            item(key = "ai_disclaimer") {
                Text(
                    text = stringResource(R.string.chat_ai_disclaimer),
                    style = MaterialTheme.typography.caption2.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = PeekWearOnSurfaceVariant.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            // Bottom Input Controls (Text keyboard default + quick toggle to voice)
            item(key = "input_controls") {
                WearChatInputBar(
                    isGenerating = uiState.isGenerating,
                    isSendingTurn = uiState.isSendingTurn,
                    inputMode = currentInputMode,
                    onPrimaryClick = {
                        val activeMode = viewModel.defaultInputMethod.value
                        launchForMode(activeMode)
                    },
                    onSecondaryClick = {
                        val activeMode = viewModel.defaultInputMethod.value
                        val secondaryMode = when (activeMode) {
                            WearInputMode.TEXT_KEYBOARD -> WearInputMode.VOICE_FIRST
                            WearInputMode.VOICE_FIRST -> WearInputMode.TEXT_KEYBOARD
                        }
                        launchForMode(secondaryMode)
                    },
                    onStopGeneration = viewModel::cancelGeneration
                )
            }
        }
    }

    // Assistant Message Context Menu (Read-only model summary + Report action)
    val menuMessage = uiState.selectedMessageForMenu
    Dialog(
        showDialog = menuMessage != null,
        onDismissRequest = viewModel::dismissMessageContextMenu
    ) {
        if (menuMessage != null) {
            val menuScroll = rememberScalingLazyListState()
            val menuFocus = rememberActiveFocusRequester()
            ScalingLazyColumn(
                state = menuScroll,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .background(PeekWearBackground)
                    .padding(horizontal = 12.dp)
                    .rotaryScrollable(
                        behavior = RotaryScrollableDefaults.behavior(menuScroll),
                        focusRequester = menuFocus
                    )
            ) {
                val servedSummary = menuMessage.servedTargetSummary
                if (!servedSummary.isNullOrBlank()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(PeekWearSurfaceContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.chat_menu_served_model_label),
                                style = MaterialTheme.typography.caption2,
                                color = PeekWearPrimary
                            )
                            Text(
                                text = servedSummary,
                                style = MaterialTheme.typography.caption1,
                                color = PeekWearOnSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                item {
                    Chip(
                        onClick = {
                            val targetId = menuMessage.id
                            viewModel.dismissMessageContextMenu()
                            onOpenReportMessage(targetId)
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.chat_menu_report_response),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = PeekWearErrorContainer,
                            contentColor = PeekWearError,
                            iconColor = PeekWearError
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Chip(
                        onClick = viewModel::dismissMessageContextMenu,
                        label = {
                            Text(
                                text = stringResource(R.string.action_close),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = PeekWearSurfaceContainerHigh,
                            contentColor = PeekWearOnBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun WearChatMessageItem(
    message: WearMessageItem,
    thumbnailFiles: Map<String, File>,
    isReportedRevealed: Boolean,
    onTapAttachment: (String, Int) -> Unit,
    onRevealReported: () -> Unit,
    onLongPressAssistant: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    val isUser = message.role == WearMessageRole.USER

    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        if (isUser) {
            val bubbleShape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 4.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(bubbleShape)
                    .background(PeekWearPrimaryContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (message.attachments.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        maxItemsInEachRow = 2
                    ) {
                        message.attachments.forEach { att ->
                            WearThumbnailTile(
                                attachment = att,
                                file = thumbnailFiles[att.id],
                                onClick = { onTapAttachment(att.id, att.payloadRevision) }
                            )
                        }
                    }
                }
                if (message.textContent.isNotBlank()) {
                    Text(
                        text = message.textContent,
                        style = MaterialTheme.typography.body2,
                        color = PeekWearOnPrimaryContainer
                    )
                }
            }
        } else {
            val bubbleShape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
            val isError = message.status == WearMessageStatus.ERROR
            val borderMod = if (isError) {
                Modifier.border(1.dp, PeekWearError.copy(alpha = 0.7f), bubbleShape)
            } else {
                Modifier
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .clip(bubbleShape)
                    .background(PeekWearSurfaceContainerHigh)
                    .then(borderMod)
                    .combinedClickable(
                        onClick = {
                            if (message.isReported) {
                                onRevealReported()
                            }
                        },
                        onLongClick = onLongPressAssistant
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                if (message.isReported && !isReportedRevealed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            tint = PeekWearOnSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.chat_reported_message_hidden),
                            style = MaterialTheme.typography.caption1,
                            color = PeekWearOnSurfaceVariant
                        )
                    }
                } else {
                    when (message.status) {
                        WearMessageStatus.LOADING -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    indicatorColor = PeekWearPrimary,
                                    trackColor = PeekWearSurfaceContainer,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.chat_status_thinking),
                                    style = MaterialTheme.typography.body2,
                                    color = PeekWearOnSurfaceVariant
                                )
                            }
                            Chip(
                                onClick = onStop,
                                label = { Text(stringResource(R.string.action_stop)) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = PeekWearSurfaceContainer,
                                    contentColor = PeekWearError,
                                    iconColor = PeekWearError
                                )
                            )
                        }

                        WearMessageStatus.ERROR -> {
                            Text(
                                text = WearUiHelpers.mapErrorMessage(
                                    context = context,
                                    errorCode = message.errorCode,
                                    errorMessage = message.errorMessage
                                ),
                                style = MaterialTheme.typography.body2,
                                color = PeekWearError
                            )
                            Chip(
                                onClick = onRetry,
                                label = { Text(stringResource(R.string.action_retry)) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = PeekWearSurfaceContainer,
                                    contentColor = PeekWearPrimary,
                                    iconColor = PeekWearPrimary
                                )
                            )
                        }

                        WearMessageStatus.CANCELLED -> {
                            Text(
                                text = stringResource(R.string.chat_status_cancelled),
                                style = MaterialTheme.typography.body2,
                                color = PeekWearOnSurfaceVariant
                            )
                            Chip(
                                onClick = onRetry,
                                label = { Text(stringResource(R.string.action_retry)) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = PeekWearSurfaceContainer,
                                    contentColor = PeekWearPrimary,
                                    iconColor = PeekWearPrimary
                                )
                            )
                        }

                        WearMessageStatus.SUCCESS -> {
                            Text(
                                text = WearUiHelpers.formatWearMarkdown(message.textContent),
                                style = MaterialTheme.typography.body2,
                                color = PeekWearOnBackground
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WearThumbnailTile(
    attachment: WearAttachmentItem,
    file: File?,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PeekWearSurfaceContainer)
                .border(1.dp, PeekWearOutlineVariant, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
        ) {
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = stringResource(R.string.cd_message_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = stringResource(R.string.cd_message_image),
                    tint = PeekWearOnSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (attachment.isTextReplaced) {
            Text(
                text = stringResource(R.string.chat_badge_text_replaced),
                style = MaterialTheme.typography.caption2.copy(fontSize = 9.sp),
                color = PeekWearOnPrimaryContainer,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WearChatInputBar(
    isGenerating: Boolean,
    isSendingTurn: Boolean,
    inputMode: WearInputMode,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    onStopGeneration: () -> Unit
) {
    if (isGenerating) {
        Chip(
            onClick = onStopGeneration,
            label = {
                Text(
                    text = stringResource(R.string.chat_stop_generation),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            colors = ChipDefaults.chipColors(
                backgroundColor = PeekWearErrorContainer,
                contentColor = PeekWearError,
                iconColor = PeekWearError
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
        ) {
            val primaryIcon = if (inputMode == WearInputMode.TEXT_KEYBOARD) {
                Icons.Rounded.Keyboard
            } else {
                Icons.Rounded.Mic
            }
            val primaryLabel = if (inputMode == WearInputMode.TEXT_KEYBOARD) {
                stringResource(R.string.chat_input_text_label)
            } else {
                stringResource(R.string.chat_input_voice_label)
            }
            val secondaryIcon = if (inputMode == WearInputMode.TEXT_KEYBOARD) {
                Icons.Rounded.Mic
            } else {
                Icons.Rounded.Keyboard
            }
            val secondaryCd = if (inputMode == WearInputMode.TEXT_KEYBOARD) {
                stringResource(R.string.cd_switch_to_voice)
            } else {
                stringResource(R.string.cd_switch_to_keyboard)
            }

            Chip(
                onClick = onPrimaryClick,
                enabled = !isSendingTurn,
                label = {
                    if (isSendingTurn) {
                        CircularProgressIndicator(
                            indicatorColor = PeekWearOnPrimary,
                            trackColor = PeekWearPrimaryContainer,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = primaryLabel,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                icon = {
                    Icon(
                        imageVector = primaryIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = ChipDefaults.chipColors(
                    backgroundColor = PeekWearPrimary,
                    contentColor = PeekWearOnPrimary,
                    iconColor = PeekWearOnPrimary
                ),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onSecondaryClick,
                enabled = !isSendingTurn,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = PeekWearSurfaceContainerHigh,
                    contentColor = PeekWearOnBackground
                ),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = secondaryIcon,
                    contentDescription = secondaryCd,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
