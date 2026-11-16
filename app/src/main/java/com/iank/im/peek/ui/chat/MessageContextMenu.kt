package com.iank.im.peek.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.ChatMessage
import com.iank.im.peek.domain.model.MessageAttachment
import com.iank.im.peek.domain.model.MessageRole

@Composable
fun MessageContextMenuDialog(
    message: ChatMessage,
    onCopyText: () -> Unit,
    onSelectText: () -> Unit,
    onEditMessage: () -> Unit,
    onDismiss: () -> Unit
) {
    val servedModelSummary = if (message.role == MessageRole.ASSISTANT) {
        val groupName = message.servedGroupName?.takeIf { it.isNotBlank() }
        val presetName = message.servedPresetName?.takeIf { it.isNotBlank() }
        val modelName = (message.servedModelName ?: message.modelIdUsed)?.takeIf { it.isNotBlank() }
        when {
            groupName != null && modelName != null ->
                "$groupName · ${presetName ?: modelName} ($modelName)"
            presetName != null && modelName != null ->
                "$presetName ($modelName)"
            modelName != null ->
                modelName
            else -> null
        }
    } else {
        null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
                if (servedModelSummary != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.menu_served_model_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = servedModelSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                ContextMenuActionRow(
                    icon = Icons.Rounded.ContentCopy,
                    label = stringResource(R.string.menu_copy_text),
                    onClick = onCopyText
                )
                ContextMenuActionRow(
                    icon = Icons.Rounded.SelectAll,
                    label = stringResource(R.string.menu_select_text),
                    onClick = onSelectText
                )
                ContextMenuActionRow(
                    icon = Icons.Rounded.Edit,
                    label = stringResource(R.string.menu_edit_message),
                    onClick = onEditMessage
                )
            }
        }
    }
}

@Composable
fun AttachmentContextMenuDialog(
    attachment: MessageAttachment,
    onCopyImage: () -> Unit,
    onReplaceImage: () -> Unit,
    onToggleTextReplacement: () -> Unit,
    onChangeResolution: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
                ContextMenuActionRow(
                    icon = Icons.Rounded.ContentCopy,
                    label = stringResource(R.string.menu_copy_image),
                    onClick = onCopyImage
                )
                ContextMenuActionRow(
                    icon = Icons.Rounded.SwapHoriz,
                    label = stringResource(R.string.menu_replace_image),
                    onClick = onReplaceImage
                )
                ContextMenuActionRow(
                    icon = if (attachment.isTextReplaced) {
                        Icons.Rounded.Image
                    } else {
                        Icons.Rounded.TextFields
                    },
                    label = stringResource(
                        if (attachment.isTextReplaced) {
                            R.string.menu_toggle_text_replace_off
                        } else {
                            R.string.menu_toggle_text_replace_on
                        }
                    ),
                    onClick = onToggleTextReplacement
                )
                ContextMenuActionRow(
                    icon = Icons.Rounded.AspectRatio,
                    label = stringResource(R.string.menu_change_resolution),
                    onClick = onChangeResolution
                )
            }
        }
    }
}

@Composable
private fun ContextMenuActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun TextSelectionDialog(
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(text = stringResource(R.string.chat_text_selection_title))
        },
        text = {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_close))
            }
        }
    )
}
