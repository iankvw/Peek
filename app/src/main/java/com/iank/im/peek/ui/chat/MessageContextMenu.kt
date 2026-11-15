package com.iank.im.peek.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.ChatMessage
import com.iank.im.peek.domain.model.MessageAttachment

@Composable
fun MessageContextMenuDialog(
    message: ChatMessage,
    onCopyText: () -> Unit,
    onSelectText: () -> Unit,
    onEditMessage: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_copy_text)) },
                    leadingContent = {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onCopyText)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_select_text)) },
                    leadingContent = {
                        Icon(Icons.Rounded.SelectAll, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onSelectText)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_edit_message)) },
                    leadingContent = {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onEditMessage)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_copy_image)) },
                    leadingContent = {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onCopyImage)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_replace_image)) },
                    leadingContent = {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onReplaceImage)
                )
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(
                                if (attachment.isTextReplaced) {
                                    R.string.menu_toggle_text_replace_off
                                } else {
                                    R.string.menu_toggle_text_replace_on
                                }
                            )
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = if (attachment.isTextReplaced) {
                                Icons.Rounded.Image
                            } else {
                                Icons.Rounded.TextFields
                            },
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable(onClick = onToggleTextReplacement)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_change_resolution)) },
                    leadingContent = {
                        Icon(Icons.Rounded.AspectRatio, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onChangeResolution)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
fun TextSelectionDialog(
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
