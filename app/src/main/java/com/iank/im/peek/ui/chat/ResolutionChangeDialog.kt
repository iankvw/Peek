package com.iank.im.peek.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.ImageResolutionPreset

@Composable
fun ResolutionPickerDialog(
    currentEffectivePreset: ImageResolutionPreset,
    onSelectOption: (ImageResolutionPreset) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.resolution_dialog_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ImageResolutionPreset.entries.forEach { preset ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectOption(preset) }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = preset == currentEffectivePreset,
                            onClick = { onSelectOption(preset) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = shortResolutionLabel(preset),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun ResolutionConfirmScopeDialog(
    selectedPreset: ImageResolutionPreset,
    onApplyEntireConversation: () -> Unit,
    onApplyThisPhotoOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    val label = shortResolutionLabel(selectedPreset)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.resolution_apply_all_title))
        },
        text = {
            Text(text = stringResource(R.string.resolution_apply_all_message, label))
        },
        confirmButton = {
            TextButton(onClick = onApplyEntireConversation) {
                Text(text = stringResource(R.string.resolution_apply_entire_conversation))
            }
        },
        dismissButton = {
            TextButton(onClick = onApplyThisPhotoOnly) {
                Text(text = stringResource(R.string.resolution_apply_this_only))
            }
        }
    )
}
