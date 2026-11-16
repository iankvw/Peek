package com.iank.im.peek.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.ApiProvider
import com.iank.im.peek.domain.model.AppSettings
import com.iank.im.peek.domain.model.ContextRetentionMode
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.model.ImageSendMethod
import com.iank.im.peek.domain.model.OpenAiImageDetail
import com.iank.im.peek.ui.chat.shortResolutionLabel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun PayloadContextSection(
    settings: AppSettings,
    selectedPreset: ApiPreset?,
    storageUsageBytes: Long,
    onUpdateSendMethod: (ImageSendMethod) -> Unit,
    onUpdateDefaultResolution: (ImageResolutionPreset) -> Unit,
    onUpdateContextRetention: (ContextRetentionMode) -> Unit,
    onUpdateAutoReplaceOldImages: (Boolean) -> Unit,
    onUpdateKeepRecentImageTurns: (Int) -> Unit,
    onUpdateOpenAiImageDetail: (OpenAiImageDetail) -> Unit,
    onUpdateOpenAiDeveloperRole: (Boolean) -> Unit,
    onUpdateBackgroundCaption: (Boolean) -> Unit,
    onUpdateCaptionModelId: (String) -> Unit,
    onCleanupStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isOpenAi = selectedPreset?.provider == ApiProvider.OPENAI

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_context_header),
            style = MaterialTheme.typography.titleMedium
        )

        // Gemini Send Method
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.settings_send_method_label),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.imageSendMethod == ImageSendMethod.FILE_API,
                    onClick = { onUpdateSendMethod(ImageSendMethod.FILE_API) },
                    label = { Text(stringResource(R.string.send_method_file_api)) }
                )
                FilterChip(
                    selected = settings.imageSendMethod == ImageSendMethod.BASE64,
                    onClick = { onUpdateSendMethod(ImageSendMethod.BASE64) },
                    label = { Text(stringResource(R.string.send_method_base64)) }
                )
            }
            if (isOpenAi) {
                Text(
                    text = stringResource(R.string.settings_openai_file_api_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Default Send Resolution
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.settings_default_resolution_label),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    ImageResolutionPreset.SAVER_1000,
                    ImageResolutionPreset.STANDARD_2000,
                    ImageResolutionPreset.HIGH_4000,
                    ImageResolutionPreset.ORIGINAL
                ).forEach { preset ->
                    FilterChip(
                        selected = settings.defaultResolutionPreset == preset,
                        onClick = { onUpdateDefaultResolution(preset) },
                        label = { Text(shortResolutionLabel(preset)) }
                    )
                }
            }
            if (settings.defaultResolutionPreset == ImageResolutionPreset.ORIGINAL) {
                Text(
                    text = stringResource(R.string.settings_original_resolution_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        HorizontalDivider()

        // Context Retention Scope
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.settings_context_retention_label),
                style = MaterialTheme.typography.labelLarge
            )
            val isAll = settings.contextRetentionMode is ContextRetentionMode.All
            val currentTurns = (settings.contextRetentionMode as? ContextRetentionMode.SlidingWindow)?.turns
                ?: ContextRetentionMode.DEFAULT_TURNS

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isAll,
                    onClick = { onUpdateContextRetention(ContextRetentionMode.All) },
                    label = { Text(stringResource(R.string.context_mode_all)) }
                )
                FilterChip(
                    selected = !isAll,
                    onClick = {
                        onUpdateContextRetention(ContextRetentionMode.SlidingWindow(currentTurns))
                    },
                    label = { Text(stringResource(R.string.context_mode_sliding, currentTurns)) }
                )
            }

            if (!isAll) {
                Text(
                    text = stringResource(R.string.settings_sliding_turns_label, currentTurns),
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = currentTurns.toFloat(),
                    onValueChange = { value ->
                        onUpdateContextRetention(
                            ContextRetentionMode.SlidingWindow(value.roundToInt().coerceIn(2, 30))
                        )
                    },
                    valueRange = 2f..30f,
                    steps = 27
                )
            }
        }

        // Auto text replace older images
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_auto_replace_old_images),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.settings_auto_replace_old_images_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.autoTextReplaceOldImages,
                onCheckedChange = onUpdateAutoReplaceOldImages
            )
        }

        if (settings.autoTextReplaceOldImages) {
            Column {
                Text(
                    text = stringResource(
                        R.string.settings_keep_recent_image_turns,
                        settings.keepRecentImageTurns
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = settings.keepRecentImageTurns.toFloat(),
                    onValueChange = { onUpdateKeepRecentImageTurns(it.roundToInt().coerceIn(1, 5)) },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }
        }

        HorizontalDivider()

        // OpenAI options
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_openai_detail_label),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.openAiImageDetail == OpenAiImageDetail.AUTO,
                    onClick = { onUpdateOpenAiImageDetail(OpenAiImageDetail.AUTO) },
                    label = { Text(stringResource(R.string.openai_detail_auto)) }
                )
                FilterChip(
                    selected = settings.openAiImageDetail == OpenAiImageDetail.LOW,
                    onClick = { onUpdateOpenAiImageDetail(OpenAiImageDetail.LOW) },
                    label = { Text(stringResource(R.string.openai_detail_low)) }
                )
                FilterChip(
                    selected = settings.openAiImageDetail == OpenAiImageDetail.HIGH,
                    onClick = { onUpdateOpenAiImageDetail(OpenAiImageDetail.HIGH) },
                    label = { Text(stringResource(R.string.openai_detail_high)) }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_openai_developer_role),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_openai_developer_role_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.openAiUseDeveloperRole,
                    onCheckedChange = onUpdateOpenAiDeveloperRole
                )
            }
        }

        HorizontalDivider()

        // Background captioning opt-in
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_bg_caption_toggle),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.settings_bg_caption_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.backgroundCaptionEnabled,
                onCheckedChange = onUpdateBackgroundCaption
            )
        }

        if (settings.backgroundCaptionEnabled) {
            var localCaptionModelId by remember { mutableStateOf(settings.captionModelId) }
            val latestOnUpdateCaptionModel by rememberUpdatedState(onUpdateCaptionModelId)
            val latestSavedCaptionModel by rememberUpdatedState(settings.captionModelId)

            LaunchedEffect(Unit) {
                snapshotFlow { localCaptionModelId }
                    .drop(1)
                    .distinctUntilChanged()
                    .debounce(400L)
                    .collect { updated ->
                        if (updated != latestSavedCaptionModel) {
                            latestOnUpdateCaptionModel(updated)
                        }
                    }
            }

            DisposableEffect(Unit) {
                onDispose {
                    if (localCaptionModelId != latestSavedCaptionModel) {
                        latestOnUpdateCaptionModel(localCaptionModelId)
                    }
                }
            }

            OutlinedTextField(
                value = localCaptionModelId,
                onValueChange = { localCaptionModelId = it },
                label = { Text(stringResource(R.string.settings_caption_model_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        HorizontalDivider()

        // Storage usage & cleanup
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.settings_storage_usage,
                        Formatter.formatShortFileSize(context, storageUsageBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = onCleanupStorage,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_cleanup_storage))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
