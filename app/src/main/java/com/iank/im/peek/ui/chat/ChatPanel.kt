package com.iank.im.peek.ui.chat

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iank.im.peek.R
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
    pendingAttachments: List<PendingAttachment>,
    inputText: String,
    canSend: Boolean,
    isSendingInActiveConversation: Boolean,
    resolveFile: (String) -> File?,
    onOpenDrawer: () -> Unit,
    onReturnToCamera: () -> Unit,
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

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = stringResource(R.string.cd_open_drawer)
                        )
                    }
                    IconButton(onClick = onReturnToCamera) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_return_to_camera)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = conversation?.title ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        conversation?.overrideResolutionPreset?.let { overridePreset ->
                            Text(
                                text = stringResource(
                                    R.string.chat_resolution_badge,
                                    shortResolutionLabel(overridePreset)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onReturnToCamera) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = stringResource(R.string.cd_return_to_camera)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.cd_open_settings)
                        )
                    }
                }
            }

            HorizontalDivider()

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

            HorizontalDivider()

            // Bottom chat composer
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
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
