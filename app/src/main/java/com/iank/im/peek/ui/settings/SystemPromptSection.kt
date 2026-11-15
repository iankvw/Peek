package com.iank.im.peek.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iank.im.peek.R
import kotlinx.coroutines.launch

@Composable
fun SystemPromptSection(
    systemPrompt: String,
    onUpdateSystemPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultPrompt = stringResource(R.string.default_system_prompt)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_system_prompt_header),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.settings_system_prompt_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = systemPrompt,
            onValueChange = onUpdateSystemPrompt,
            label = { Text(stringResource(R.string.settings_system_prompt_label)) },
            placeholder = { Text(defaultPrompt) },
            minLines = 5,
            maxLines = 12,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusEvent { state ->
                    if (state.isFocused) {
                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                }
        )

        TextButton(
            onClick = { onUpdateSystemPrompt("") },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.settings_reset_system_prompt))
        }
    }
}
