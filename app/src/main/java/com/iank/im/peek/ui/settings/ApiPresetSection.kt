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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import com.iank.im.peek.domain.model.GroupRoutingMode
import com.iank.im.peek.domain.model.ImagePayloadFormat
import com.iank.im.peek.domain.model.ModelGroup
import com.iank.im.peek.domain.model.RoundRobinSubMode
import com.iank.im.peek.ui.main.ConnectionTestUiState
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ApiPresetSection(
    presets: List<ApiPreset>,
    modelGroups: List<ModelGroup>,
    selectedPresetId: String?,
    connectionTestStatus: ConnectionTestUiState,
    onSelectPreset: (String) -> Unit,
    onLoadApiKey: suspend (String) -> String,
    onSavePreset: (ApiPreset, String) -> Unit,
    onClonePreset: (ApiPreset, String, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSaveModelGroup: (ModelGroup) -> Unit,
    onDeleteModelGroup: (String) -> Unit,
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
    var modelId by rememberSaveable { mutableStateOf("gemini-flash-latest") }
    var customUrlEnabled by rememberSaveable { mutableStateOf(false) }
    var customUrlInput by rememberSaveable { mutableStateOf("") }
    var payloadFormat by rememberSaveable { mutableStateOf(ImagePayloadFormat.WEBP) }
    var maxRequestMbText by rememberSaveable { mutableStateOf("") }

    // Model Group editor state
    var editingGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var isCreatingGroup by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteGroup by remember { mutableStateOf<ModelGroup?>(null) }
    var groupName by rememberSaveable { mutableStateOf("") }
    var groupRoutingMode by rememberSaveable { mutableStateOf(GroupRoutingMode.FALLBACK) }
    var groupRrSubMode by rememberSaveable { mutableStateOf(RoundRobinSubMode.SEQUENTIAL) }
    var groupRrFailover by rememberSaveable { mutableStateOf(true) }
    var groupMemberIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    val coroutineScope = rememberCoroutineScope()
    val bringIntoView = remember { BringIntoViewRequester() }

    fun populateGroupEditor(group: ModelGroup?) {
        if (group == null) {
            editingGroupId = null
            isCreatingGroup = true
            groupName = ""
            groupRoutingMode = GroupRoutingMode.FALLBACK
            groupRrSubMode = RoundRobinSubMode.SEQUENTIAL
            groupRrFailover = true
            groupMemberIds = presets.take(2).map { it.id }
        } else {
            editingGroupId = group.id
            isCreatingGroup = false
            groupName = group.name
            groupRoutingMode = group.routingMode
            groupRrSubMode = group.roundRobinSubMode
            groupRrFailover = group.failoverOnRoundRobinError
            val validPresetIds = presets.map { it.id }.toSet()
            groupMemberIds = group.memberPresetIds.filter { it in validPresetIds }
        }
    }

    fun populateEditor(preset: ApiPreset?) {
        onResetConnectionTest()
        if (preset == null) {
            editingPresetId = null
            isCreatingNew = true
            alias = ""
            provider = ApiProvider.GEMINI
            apiKey = ""
            showApiKey = false
            modelId = "gemini-flash-latest"
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

    @Composable
    fun PresetEditorCard() {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            if (editingPresetId != null) R.string.settings_edit_preset else R.string.settings_add_preset
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (presets.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                editingPresetId = null
                                isCreatingNew = false
                                onResetConnectionTest()
                            }
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }

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
                                    modelId = "gemini-flash-latest"
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

                    val currentEditingId = editingPresetId
                    val cloneBaseName = alias.trim().ifBlank { modelId.trim() }
                    val clonedAliasText = stringResource(
                        R.string.settings_preset_clone_name_format,
                        cloneBaseName
                    )

                    if (currentEditingId != null) {
                        OutlinedButton(
                            onClick = {
                                val candidate = buildCandidatePreset()
                                val cloned = candidate.copy(
                                    id = UUID.randomUUID().toString(),
                                    alias = clonedAliasText,
                                    createdAt = System.currentTimeMillis()
                                )
                                onClonePreset(cloned, currentEditingId, apiKey.trim())
                            },
                            enabled = canSave,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_clone_preset))
                        }
                    }

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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    val isSelected = preset.id == selectedPresetId
                    val isEditingThis = editingPresetId == preset.id
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
                            IconButton(
                                onClick = {
                                    if (isEditingThis) {
                                        editingPresetId = null
                                        isCreatingNew = false
                                        onResetConnectionTest()
                                    } else {
                                        populateEditor(preset)
                                    }
                                }
                            ) {
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

                    // Inline edit form rendered directly below the selected preset card
                    if (isEditingThis) {
                        PresetEditorCard()
                    }
                }
            }
        }

        if (isCreatingNew && editingPresetId == null) {
            PresetEditorCard()
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Model Groups Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.settings_model_groups_header),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(
                onClick = { populateGroupEditor(null) },
                enabled = presets.size >= 2
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.settings_add_group))
            }
        }

        if (presets.size < 2) {
            Text(
                text = stringResource(R.string.settings_group_min_presets_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val presetMap = remember(presets) { presets.associateBy { it.id } }

        if (modelGroups.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                modelGroups.forEach { group ->
                    val isSelected = group.id == selectedPresetId
                    val memberNames = group.memberPresetIds.mapNotNull { id ->
                        presetMap[id]?.let { p -> p.alias.ifBlank { p.modelId } }
                    }
                    val modeLabel = when (group.routingMode) {
                        GroupRoutingMode.FALLBACK -> stringResource(R.string.group_routing_fallback)
                        GroupRoutingMode.ROUND_ROBIN -> when (group.roundRobinSubMode) {
                            RoundRobinSubMode.SEQUENTIAL ->
                                stringResource(R.string.group_routing_rr_sequential_short)
                            RoundRobinSubMode.RANDOM ->
                                stringResource(R.string.group_routing_rr_random_short)
                        }
                    }

                    Surface(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPreset(group.id) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectPreset(group.id) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "$modeLabel · ${memberNames.joinToString(" → ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { populateGroupEditor(group) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = stringResource(R.string.settings_edit_group)
                                )
                            }
                            IconButton(onClick = { pendingDeleteGroup = group }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.cd_delete_group)
                                )
                            }
                        }
                    }
                }
            }
        }

        if ((isCreatingGroup || editingGroupId != null) && presets.size >= 2) {
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
                            if (editingGroupId != null) R.string.settings_edit_group else R.string.settings_add_group
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text(stringResource(R.string.settings_group_name_label)) },
                        placeholder = { Text(stringResource(R.string.settings_group_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.settings_group_routing_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = groupRoutingMode == GroupRoutingMode.FALLBACK,
                            onClick = { groupRoutingMode = GroupRoutingMode.FALLBACK },
                            label = { Text(stringResource(R.string.group_routing_fallback)) }
                        )
                        FilterChip(
                            selected = groupRoutingMode == GroupRoutingMode.ROUND_ROBIN,
                            onClick = { groupRoutingMode = GroupRoutingMode.ROUND_ROBIN },
                            label = { Text(stringResource(R.string.group_routing_round_robin)) }
                        )
                    }

                    Text(
                        text = stringResource(
                            if (groupRoutingMode == GroupRoutingMode.FALLBACK) {
                                R.string.group_routing_fallback_desc
                            } else {
                                R.string.group_routing_round_robin_desc
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (groupRoutingMode == GroupRoutingMode.ROUND_ROBIN) {
                        Text(
                            text = stringResource(R.string.settings_group_rr_submode_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = groupRrSubMode == RoundRobinSubMode.SEQUENTIAL,
                                onClick = { groupRrSubMode = RoundRobinSubMode.SEQUENTIAL },
                                label = { Text(stringResource(R.string.group_rr_submode_sequential)) }
                            )
                            FilterChip(
                                selected = groupRrSubMode == RoundRobinSubMode.RANDOM,
                                onClick = { groupRrSubMode = RoundRobinSubMode.RANDOM },
                                label = { Text(stringResource(R.string.group_rr_submode_random)) }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.settings_group_rr_failover_toggle),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Switch(
                                checked = groupRrFailover,
                                onCheckedChange = { groupRrFailover = it }
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.settings_group_members_label),
                        style = MaterialTheme.typography.labelLarge
                    )

                    val sortedPresetsForGroup = remember(presets, groupMemberIds) {
                        val byId = presets.associateBy { it.id }
                        val selectedOrdered = groupMemberIds.mapNotNull { byId[it] }
                        val selectedSet = groupMemberIds.toSet()
                        val unselected = presets.filterNot { it.id in selectedSet }
                        selectedOrdered + unselected
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sortedPresetsForGroup.forEach { preset ->
                            val isChecked = preset.id in groupMemberIds
                            val orderIndex = groupMemberIds.indexOf(preset.id)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            groupMemberIds = if (checked) {
                                                groupMemberIds + preset.id
                                            } else {
                                                groupMemberIds - preset.id
                                            }
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (orderIndex >= 0) {
                                                "#${orderIndex + 1}  ${preset.alias.ifBlank { preset.modelId }}"
                                            } else {
                                                preset.alias.ifBlank { preset.modelId }
                                            },
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "${preset.provider.name} · ${preset.modelId}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (orderIndex >= 0) {
                                        IconButton(
                                            onClick = {
                                                if (orderIndex > 0) {
                                                    val mutable = groupMemberIds.toMutableList()
                                                    val tmp = mutable[orderIndex - 1]
                                                    mutable[orderIndex - 1] = mutable[orderIndex]
                                                    mutable[orderIndex] = tmp
                                                    groupMemberIds = mutable
                                                }
                                            },
                                            enabled = orderIndex > 0,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                if (orderIndex < groupMemberIds.lastIndex) {
                                                    val mutable = groupMemberIds.toMutableList()
                                                    val tmp = mutable[orderIndex + 1]
                                                    mutable[orderIndex + 1] = mutable[orderIndex]
                                                    mutable[orderIndex] = tmp
                                                    groupMemberIds = mutable
                                                }
                                            },
                                            enabled = orderIndex < groupMemberIds.lastIndex,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.settings_group_base64_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val canSaveGroup = groupName.trim().isNotEmpty() && groupMemberIds.size >= 2

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                editingGroupId = null
                                isCreatingGroup = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }

                        Button(
                            onClick = {
                                val existingGroup = modelGroups.firstOrNull { it.id == editingGroupId }
                                val candidateGroup = ModelGroup(
                                    id = existingGroup?.id ?: UUID.randomUUID().toString(),
                                    name = groupName.trim(),
                                    memberPresetIds = groupMemberIds,
                                    routingMode = groupRoutingMode,
                                    roundRobinSubMode = groupRrSubMode,
                                    failoverOnRoundRobinError = groupRrFailover,
                                    lastUsedIndex = existingGroup?.lastUsedIndex ?: -1,
                                    createdAt = existingGroup?.createdAt ?: System.currentTimeMillis()
                                )
                                onSaveModelGroup(candidateGroup)
                                editingGroupId = null
                                isCreatingGroup = false
                            },
                            enabled = canSaveGroup,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            }
        }
    }

    pendingDeleteGroup?.let { groupToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteGroup = null },
            title = { Text(stringResource(R.string.settings_group_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_group_delete_message,
                        groupToDelete.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteModelGroup(groupToDelete.id)
                        if (editingGroupId == groupToDelete.id) {
                            editingGroupId = null
                            isCreatingGroup = false
                        }
                        pendingDeleteGroup = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteGroup = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
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
