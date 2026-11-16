package com.iank.im.peek.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.ChatMessage
import com.iank.im.peek.domain.model.Conversation
import com.iank.im.peek.domain.model.MessageAttachment
import com.iank.im.peek.domain.model.PendingAttachment
import com.iank.im.peek.ui.camera.PendingAttachmentsStrip
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatPanel(
    conversation: Conversation?,
    messages: List<ChatMessage>,
    presets: List<ApiPreset>,
    selectedPreset: ApiPreset?,
    pendingAttachments: List<PendingAttachment>,
    inputText: String,
    canSend: Boolean,
    isSendingInActiveConversation: Boolean,
    resolveFile: (String) -> File?,
    onOpenDrawer: () -> Unit,
    onReturnToCamera: () -> Unit,
    onNewConversation: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onInputChanged: (String) -> Unit,
    onRemovePending: (String) -> Unit,
    onPickFromGallery: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRetryMessage: (String) -> Unit,
    onTapAttachment: (MessageAttachment) -> Unit,
    onLongPressAttachment: (MessageAttachment, String) -> Unit,
    onLongPressMessage: (ChatMessage) -> Unit,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val reversedMessages = remember(messages) { messages.asReversed() }
    var isPresetMenuOpen by remember { mutableStateOf(false) }
    var dragDistanceY by remember { mutableFloatStateOf(0f) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with sheet drag handle + preset selector chip + new conversation button
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { dragDistanceY = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                dragDistanceY += dragAmount
                            },
                            onDragEnd = {
                                if (dragDistanceY > 72f) {
                                    onReturnToCamera()
                                }
                                dragDistanceY = 0f
                            }
                        )
                    }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    // Drag handle pill for returning to viewfinder
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onReturnToCamera)
                            .padding(top = 8.dp, bottom = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onOpenDrawer) {
                                Icon(
                                    imageVector = Icons.Rounded.Menu,
                                    contentDescription = stringResource(R.string.cd_open_drawer)
                                )
                            }

                            Box {
                                Surface(
                                    onClick = {
                                        if (presets.isEmpty()) {
                                            onOpenSettings()
                                        } else {
                                            isPresetMenuOpen = !isPresetMenuOpen
                                        }
                                    },
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(22.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                            .widthIn(max = 200.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = stringResource(R.string.cd_select_preset),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = selectedPreset?.alias?.ifBlank { selectedPreset.modelId }
                                                ?: stringResource(R.string.preset_none_configured),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (presets.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = isPresetMenuOpen && presets.isNotEmpty(),
                                    onDismissRequest = { isPresetMenuOpen = false }
                                ) {
                                    presets.forEach { preset ->
                                        val isSelected = preset.id == selectedPreset?.id
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = preset.alias.ifBlank { preset.modelId },
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = "${preset.provider.name} · ${preset.modelId}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            trailingIcon = {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            },
                                            onClick = {
                                                onSelectPreset(preset.id)
                                                isPresetMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }

                            conversation?.overrideResolutionPreset?.let { overridePreset ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.chat_resolution_badge,
                                            shortResolutionLabel(overridePreset)
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            onClick = onNewConversation,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.cd_new_conversation),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.drawer_new_conversation),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Message list (reverseLayout = true keeps latest message anchored above IME)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .consumeWindowInsets(WindowInsets.navigationBars)
            ) {
                if (messages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_empty_messages),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                } else {
                    LazyColumn(
                        reverseLayout = true,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(reversedMessages, key = { it.id }) { msg ->
                            MessageBubble(
                                message = msg,
                                resolveFile = resolveFile,
                                onTapAttachment = onTapAttachment,
                                onLongPressAttachment = onLongPressAttachment,
                                onLongPressMessage = onLongPressMessage,
                                onRetry = onRetryMessage,
                                onStop = onStop,
                                onCopyCode = onCopyCode
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Bottom chat composer
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(top = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pendingAttachments.isNotEmpty()) {
                        PendingAttachmentsStrip(
                            pendingAttachments = pendingAttachments,
                            resolveFile = resolveFile,
                            onRemovePending = onRemovePending
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        IconButton(onClick = onPickFromGallery) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = stringResource(R.string.cd_pick_from_gallery)
                            )
                        }
                        IconButton(onClick = onReturnToCamera) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = stringResource(R.string.cd_return_to_camera)
                            )
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = onInputChanged,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.input_hint_follow_up),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            shape = RoundedCornerShape(22.dp),
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        if (isSendingInActiveConversation) {
                            Surface(
                                onClick = onStop,
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = stringResource(R.string.action_stop),
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else {
                            Surface(
                                onClick = onSend,
                                enabled = canSend,
                                color = if (canSend) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = stringResource(R.string.cd_send_message),
                                        tint = if (canSend) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
