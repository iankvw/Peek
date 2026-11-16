package com.iank.im.peek.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.ChatMessage
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.model.MessageAttachment
import com.iank.im.peek.domain.model.MessageRole
import com.iank.im.peek.domain.model.MessageStatus
import com.iank.im.peek.ui.common.ErrorUiMapper
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    resolveFile: (String) -> File?,
    onTapAttachment: (MessageAttachment) -> Unit,
    onLongPressAttachment: (MessageAttachment, String) -> Unit,
    onLongPressMessage: (ChatMessage) -> Unit,
    onRetry: (String) -> Unit,
    onStop: () -> Unit,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val horizontalAlign = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val bubbleTextColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }

    Column(
        horizontalAlignment = horizontalAlign,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        if (message.attachments.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .widthIn(max = 310.dp)
            ) {
                message.attachments.forEach { attachment ->
                    AttachmentThumbnailCard(
                        attachment = attachment,
                        resolveFile = resolveFile,
                        onClick = { onTapAttachment(attachment) },
                        onLongClick = { onLongPressAttachment(attachment, message.id) }
                    )
                }
            }
        }

        when {
            !isUser && message.status == MessageStatus.LOADING -> {
                Surface(
                    color = bubbleColor,
                    shape = shape,
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = stringResource(R.string.chat_status_thinking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = bubbleTextColor
                        )
                        OutlinedButton(
                            onClick = onStop,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(R.string.action_stop))
                        }
                    }
                }
            }

            !isUser && message.status == MessageStatus.ERROR -> {
                val errorText = ErrorUiMapper.fromPersistedError(
                    errorCode = message.errorCode,
                    detailMessage = message.errorMessage
                ).asString()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = shape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = errorText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedButton(
                            onClick = { onRetry(message.id) },
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.action_retry),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            !isUser && message.status == MessageStatus.CANCELLED -> {
                Surface(
                    color = bubbleColor,
                    shape = shape,
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.chat_status_cancelled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = bubbleTextColor.copy(alpha = 0.8f)
                        )
                        OutlinedButton(onClick = { onRetry(message.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(R.string.action_retry))
                        }
                    }
                }
            }

            message.textContent.isNotBlank() -> {
                Surface(
                    color = bubbleColor,
                    shape = shape,
                    modifier = Modifier
                        .widthIn(max = 340.dp)
                        .clip(shape)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onLongPressMessage(message) }
                        )
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (isUser) {
                            Text(
                                text = message.textContent,
                                style = MaterialTheme.typography.bodyLarge,
                                color = bubbleTextColor
                            )
                        } else {
                            MarkdownText(
                                markdown = message.textContent,
                                textColor = bubbleTextColor,
                                onCopyCode = onCopyCode
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentThumbnailCard(
    attachment: MessageAttachment,
    resolveFile: (String) -> File?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val thumbFile = resolveFile(attachment.thumbnailRelPath)
        ?: resolveFile(attachment.originalRelPath)
    val cardShape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .size(116.dp)
            .clip(cardShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, cardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = thumbFile,
            contentDescription = stringResource(R.string.cd_message_image),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (attachment.isTextReplaced) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TextFields,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.chat_badge_text_replaced),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (attachment.requestedPreset != ImageResolutionPreset.DEFAULT) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = shortResolutionLabel(attachment.requestedPreset),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun shortResolutionLabel(preset: ImageResolutionPreset): String = when (preset) {
    ImageResolutionPreset.DEFAULT -> stringResource(R.string.resolution_preset_default)
    ImageResolutionPreset.ORIGINAL -> stringResource(R.string.resolution_preset_original)
    ImageResolutionPreset.HIGH_4000 -> stringResource(R.string.resolution_preset_high_4000)
    ImageResolutionPreset.STANDARD_2000 -> stringResource(R.string.resolution_preset_standard_2000)
    ImageResolutionPreset.SAVER_1000 -> stringResource(R.string.resolution_preset_saver_1000)
}
