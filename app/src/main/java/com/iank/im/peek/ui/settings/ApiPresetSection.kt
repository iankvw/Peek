package com.iank.im.peek.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.iank.im.peek.R
import com.iank.im.peek.data.remote.openai.BaseUrlNormalizationResult
import com.iank.im.peek.data.remote.openai.BaseUrlNormalizer
import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.ApiProvider
import com.iank.im.peek.domain.model.ImagePayloadFormat
import com.iank.im.peek.ui.main.ConnectionTestUiState
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ApiPresetSection(
    presets: List<ApiPreset>,
    selectedPresetId: String?,
    connectionTestStatus: ConnectionTestUiState,
    onSelectPreset: (String) -> Unit,
    onLoadApiKey: suspend (String) -> String,
    onSavePreset: (ApiPreset, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onTestConnection: (ApiPreset, String) -> Unit,
    onResetConnectionTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingPresetId by rememberSaveable { mutableStateOf<String?>(null) }
    var isCreatingNew by rememberSaveable { mutableStateOf(presets.isEmpty()) }
    var pendingDeletePreset by remember { mutableStateOf<ApiPreset?>(null) }

    var alias by rememberSaveable { mutableStateOf("") }
    var provider by rememberSaveable { mutableStateOf(ApiProvider.GEMINI) }
    var apiKey by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var modelId by rememberSaveable { mutableStateOf("gemini-2.5-flash") }
    var customUrlEnabled by rememberSaveable { mutableStateOf(false) }
    var customUrlInput by rememberSaveable { mutableStateOf("") }
    var payloadFormat by rememberSaveable { mutableStateOf(ImagePayloadFormat.WEBP) }
    var maxRequestMbText by rememberSaveable { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val bringIntoView = remember { BringIntoViewRequester() }

    fun populateEditor(preset: ApiPreset?) {
        onResetConnectionTest()
        if (preset == null) {
            editingPresetId = null
            isCreatingNew = true
            alias = ""
            provider = ApiProvider.GEMINI
            apiKey = ""
            showApiKey = false
            modelId = "gemini-2.5-flash"
            customUrlEnabled = false
            customUrlInput = ""
            payloadFormat = ImagePayloadFormat.WEBP
            maxRequestMbText = ""
        } else {
            editingPresetId = preset.id
            isCreatingNew = false
            alias = preset.alias
            provider = preset.provider
            modelId = preset.modelId
            customUrlEnabled = preset.customBaseUrlEnabled
            customUrlInput = preset.customBaseUrl.orEmpty()
            payloadFormat = preset.imagePayloadFormat
            maxRequestMbText = preset.maxRequestBytes?.let { (it / (1024L * 1024L)).toString() }.orEmpty()
            showApiKey = false
            coroutineScope.launch {
                apiKey = onLoadApiKey(preset.id)
            }
        }
    }

    LaunchedEffect(presets.isEmpty()) {
        if (presets.isEmpty() && !isCreatingNew && editingPresetId == null) {
            populateEditor(null)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.settings_api_presets_header),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = { populateEditor(null) }) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.settings_add_preset))
            }
        }

        if (presets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { preset ->
                    val isSelected = preset.id == selectedPresetId
                    Surface(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPreset(preset.id) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectPreset(preset.id) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
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
                            IconButton(onClick = { populateEditor(preset) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = stringResource(R.string.settings_edit_preset)
                                )
                            }
                            IconButton(onClick = { pendingDeletePreset = preset }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.cd_delete_preset)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isCreatingNew || editingPresetId != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (editingPresetId != null) R.string.settings_edit_preset else R.string.settings_add_preset
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text(stringResource(R.string.settings_preset_alias_label)) },
                        placeholder = { Text(stringResource(R.string.settings_preset_alias_placeholder)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bringIntoView)
                            .onFocusEvent { if (it.isFocused) coroutineScope.launch { bringIntoView.bringIntoView() } }
                    )

                    Text(
                        text = stringResource(R.string.settings_preset_provider_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = provider == ApiProvider.GEMINI,
                            onClick = {
                                provider = ApiProvider.GEMINI
                                customUrlEnabled = false
                                if (modelId.isBlank() || modelId.startsWith("gpt-")) {
                                    modelId = "gemini-2.5-flash"
                                }
                                onResetConnectionTest()
                            },
                            label = { Text(stringResource(R.string.provider_gemini)) }
                        )
                        FilterChip(
                            selected = provider == ApiProvider.OPENAI,
                            onClick = {
                                provider = ApiProvider.OPENAI
                                if (modelId.isBlank() || modelId.startsWith("gemini-")) {
                                    modelId = "gpt-4o"
                                }
                                onResetConnectionTest()
                            },
                            label = { Text(stringResource(R.string.provider_openai)) }
                        )
                    }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(stringResource(R.string.settings_preset_apikey_label)) },
                        placeholder = { Text(stringResource(R.string.settings_preset_apikey_placeholder)) },
                        supportingText = if (provider == ApiProvider.OPENAI && customUrlEnabled) {
                            { Text(stringResource(R.string.settings_preset_apikey_optional_hint)) }
                        } else null,
                        visualTransformation = if (showApiKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = stringResource(R.string.cd_toggle_api_key_visibility)
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        label = { Text(stringResource(R.string.settings_preset_model_label)) },
                        placeholder = { Text(stringResource(R.string.settings_preset_model_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val urlNorm = remember(provider, customUrlEnabled, customUrlInput) {
                        if (provider == ApiProvider.OPENAI) {
                            BaseUrlNormalizer.normalize(customUrlInput, customUrlEnabled)
                        } else {
                            null
                        }
                    }

                    if (provider == ApiProvider.OPENAI) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.settings_preset_custom_url_toggle),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = customUrlEnabled,
                                onCheckedChange = { customUrlEnabled = it }
                            )
                        }

                        if (customUrlEnabled) {
                            OutlinedTextField(
                                value = customUrlInput,
                                onValueChange = { customUrlInput = it },
                                label = { Text(stringResource(R.string.settings_preset_custom_url_label)) },
                                placeholder = { Text(stringResource(R.string.settings_preset_custom_url_placeholder)) },
                                isError = urlNorm is BaseUrlNormalizationResult.MissingSchemeOrInvalid,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            when (urlNorm) {
                                is BaseUrlNormalizationResult.Valid -> {
                                    Text(
                                        text = stringResource(
                                            R.string.settings_preset_endpoint_preview,
                                            urlNorm.chatCompletionsUrl
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (urlNorm.isCleartextHttp && apiKey.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.WarningAmber,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.settings_preset_http_key_warning),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }
                                BaseUrlNormalizationResult.MissingSchemeOrInvalid -> {
                                    Text(
                                        text = stringResource(R.string.settings_preset_url_invalid),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                null -> Unit
                            }

                            Text(
                                text = stringResource(R.string.settings_preset_local_host_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.settings_preset_image_format_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = payloadFormat == ImagePayloadFormat.WEBP,
                            onClick = { payloadFormat = ImagePayloadFormat.WEBP },
                            label = { Text(stringResource(R.string.image_format_webp)) }
                        )
                        FilterChip(
                            selected = payloadFormat == ImagePayloadFormat.JPEG,
                            onClick = { payloadFormat = ImagePayloadFormat.JPEG },
                            label = { Text(stringResource(R.string.image_format_jpeg)) }
                        )
                    }

                    OutlinedTextField(
                        value = maxRequestMbText,
                        onValueChange = { input ->
                            maxRequestMbText = input.filter { it.isDigit() }
                        },
                        label = { Text(stringResource(R.string.settings_preset_max_bytes_label)) },
                        placeholder = { Text(stringResource(R.string.settings_preset_max_bytes_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    fun buildCandidatePreset(): ApiPreset {
                        val existing = presets.firstOrNull { it.id == editingPresetId }
                        val normalizedBase = if (provider == ApiProvider.OPENAI && customUrlEnabled) {
                            (BaseUrlNormalizer.normalize(customUrlInput, true) as? BaseUrlNormalizationResult.Valid)
                                ?.normalizedBaseUrl
                        } else {
                            null
                        }
                        val maxBytes = maxRequestMbText.toLongOrNull()
                            ?.takeIf { it > 0L }
                            ?.let { it * 1024L * 1024L }

                        return ApiPreset(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            alias = alias.trim().ifBlank { modelId.trim() },
                            provider = provider,
                            customBaseUrlEnabled = provider == ApiProvider.OPENAI && customUrlEnabled,
                            customBaseUrl = normalizedBase,
                            modelId = modelId.trim(),
                            imagePayloadFormat = payloadFormat,
                            maxRequestBytes = maxBytes,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis()
                        )
                    }

                    val urlValidForSave = provider == ApiProvider.GEMINI ||
                        !customUrlEnabled ||
                        urlNorm is BaseUrlNormalizationResult.Valid
                    val canSave = modelId.isNotBlank() && urlValidForSave

                    when (connectionTestStatus) {
                        ConnectionTestUiState.Idle -> Unit
                        ConnectionTestUiState.Testing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = stringResource(R.string.settings_connection_testing),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        ConnectionTestUiState.Success -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.settings_connection_success),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        is ConnectionTestUiState.Failed -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = connectionTestStatus.message.asString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onTestConnection(buildCandidatePreset(), apiKey.trim()) },
                            enabled = urlValidForSave && connectionTestStatus !is ConnectionTestUiState.Testing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.action_test_connection))
                        }

                        Button(
                            onClick = {
                                val candidate = buildCandidatePreset()
                                onSavePreset(candidate, apiKey.trim())
                                editingPresetId = null
                                isCreatingNew = false
                            },
                            enabled = canSave,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            }
        }
    }

    pendingDeletePreset?.let { presetToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeletePreset = null },
            title = { Text(stringResource(R.string.settings_preset_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_preset_delete_message,
                        presetToDelete.alias.ifBlank { presetToDelete.modelId }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePreset(presetToDelete.id)
                        if (editingPresetId == presetToDelete.id) {
                            editingPresetId = null
                            isCreatingNew = false
                        }
                        pendingDeletePreset = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePreset = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
