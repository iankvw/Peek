package com.iank.im.peek.ui.chat

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.ChatMessage
import com.iank.im.peek.domain.model.Conversation
import com.iank.im.peek.domain.model.GroupRoutingMode
import com.iank.im.peek.domain.model.MessageAttachment
import com.iank.im.peek.domain.model.PendingAttachment
import com.iank.im.peek.domain.model.RoundRobinSubMode
import com.iank.im.peek.domain.model.SelectableTarget
import com.iank.im.peek.ui.camera.PendingAttachmentsStrip
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatPanel(
    conversation: Conversation?,
    messages: List<ChatMessage>,
    selectableTargets: List<SelectableTarget>,
    activeTarget: SelectableTarget?,
    pendingAttachments: List<PendingAttachment>,
    inputText: String,
    canSend: Boolean,
    isSendingInActiveConversation: Boolean,
    resolveFile: (String) -> File?,
    onOpenDrawer: () -> Unit,
    onReturnToCamera: () -> Unit,
    onNewConversation: () -> Unit,
    onSelectTarget: (String) -> Unit,
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

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with preset selector chip + compact new conversation icon button
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
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
                                    if (selectableTargets.isEmpty()) {
                                        onOpenSettings()
                                    } else {
                                        isPresetMenuOpen = !isPresetMenuOpen
                                    }
                                },
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = CircleShape,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                        .widthIn(max = 210.dp)
                                ) {
                                    Text(
                                        text = activeTarget?.displayName
                                            ?: stringResource(R.string.preset_none_configured),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (activeTarget?.isGroup == true) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                text = stringResource(R.string.group_badge_label),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (selectableTargets.isNotEmpty()) {
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
                                expanded = isPresetMenuOpen && selectableTargets.isNotEmpty(),
                                onDismissRequest = { isPresetMenuOpen = false }
                            ) {
                                selectableTargets.forEach { target ->
                                    val isSelected = target.id == activeTarget?.id
                                    DropdownMenuItem(
                                        text = {
                                            when (target) {
                                                is SelectableTarget.PresetTarget -> {
                                                    Column {
                                                        Text(
                                                            text = target.displayName,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        Text(
                                                            text = "${target.preset.provider.name} · ${target.preset.modelId}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                is SelectableTarget.GroupTarget -> {
                                                    val modeText = when (target.group.routingMode) {
                                                        GroupRoutingMode.FALLBACK ->
                                                            stringResource(R.string.group_routing_fallback)
                                                        GroupRoutingMode.ROUND_ROBIN ->
                                                            when (target.group.roundRobinSubMode) {
                                                                RoundRobinSubMode.SEQUENTIAL ->
                                                                    stringResource(R.string.group_routing_rr_sequential_short)
                                                                RoundRobinSubMode.RANDOM ->
                                                                    stringResource(R.string.group_routing_rr_random_short)
                                                            }
                                                    }
                                                    Column {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = target.displayName,
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                                shape = CircleShape
                                                            ) {
                                                                Text(
                                                                    text = stringResource(R.string.group_badge_label),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = "$modeText · ${target.memberNames.joinToString(", ")}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
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
                                            onSelectTarget(target.id)
                                            isPresetMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        conversation?.overrideResolutionPreset?.let { overridePreset ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
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

                    IconButton(onClick = onNewConversation) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.cd_new_conversation),
                            tint = MaterialTheme.colorScheme.primary
                        )
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
