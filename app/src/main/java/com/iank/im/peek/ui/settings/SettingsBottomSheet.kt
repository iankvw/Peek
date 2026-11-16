package com.iank.im.peek.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.AppLanguage
import com.iank.im.peek.domain.model.AppSettings
import com.iank.im.peek.domain.model.CameraLensOption
import com.iank.im.peek.domain.model.CaptureResolutionOption
import com.iank.im.peek.domain.model.ContextRetentionMode
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.model.ImageSendMethod
import com.iank.im.peek.domain.model.ModelGroup
import com.iank.im.peek.domain.model.OpenAiImageDetail
import com.iank.im.peek.ui.main.ConnectionTestUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    presets: List<ApiPreset>,
    modelGroups: List<ModelGroup>,
    selectedTargetId: String?,
    selectedPreset: ApiPreset?,
    settings: AppSettings,
    storageUsageBytes: Long,
    connectionTestStatus: ConnectionTestUiState,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onLoadApiKey: suspend (String) -> String,
    onSavePreset: (ApiPreset, String?) -> Unit,
    onClonePreset: (ApiPreset, String, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSaveModelGroup: (ModelGroup) -> Unit,
    onDeleteModelGroup: (String) -> Unit,
    onTestConnection: (ApiPreset, String) -> Unit,
    onResetConnectionTest: () -> Unit,
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
    onUpdateDefaultLens: (CameraLensOption) -> Unit,
    onUpdateCaptureResolution: (CaptureResolutionOption) -> Unit,
    onUpdateLanguage: (AppLanguage) -> Unit,
    onUpdateSystemPrompt: (String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.action_close)
                            )
                        }
                    }

                    val tabs = listOf(
                        R.string.settings_tab_api,
                        R.string.settings_tab_payload,
                        R.string.settings_tab_camera,
                        R.string.settings_tab_prompt
                    )

                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        edgePadding = 16.dp
                    ) {
                        tabs.forEachIndexed { index, titleRes ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(stringResource(titleRes)) }
                            )
                        }
                    }

                    key(selectedTab) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            when (selectedTab) {
                                0 -> ApiPresetSection(
                                    presets = presets,
                                    modelGroups = modelGroups,
                                    selectedPresetId = selectedTargetId,
                                    connectionTestStatus = connectionTestStatus,
                                    onSelectPreset = onSelectPreset,
                                    onLoadApiKey = onLoadApiKey,
                                    onSavePreset = onSavePreset,
                                    onClonePreset = onClonePreset,
                                    onDeletePreset = onDeletePreset,
                                    onSaveModelGroup = onSaveModelGroup,
                                    onDeleteModelGroup = onDeleteModelGroup,
                                    onTestConnection = onTestConnection,
                                    onResetConnectionTest = onResetConnectionTest
                                )
                                1 -> PayloadContextSection(
                                    settings = settings,
                                    selectedPreset = selectedPreset,
                                    storageUsageBytes = storageUsageBytes,
                                    onUpdateSendMethod = onUpdateSendMethod,
                                    onUpdateDefaultResolution = onUpdateDefaultResolution,
                                    onUpdateContextRetention = onUpdateContextRetention,
                                    onUpdateAutoReplaceOldImages = onUpdateAutoReplaceOldImages,
                                    onUpdateKeepRecentImageTurns = onUpdateKeepRecentImageTurns,
                                    onUpdateOpenAiImageDetail = onUpdateOpenAiImageDetail,
                                    onUpdateOpenAiDeveloperRole = onUpdateOpenAiDeveloperRole,
                                    onUpdateBackgroundCaption = onUpdateBackgroundCaption,
                                    onUpdateCaptionModelId = onUpdateCaptionModelId,
                                    onCleanupStorage = onCleanupStorage
                                )
                                2 -> CameraConfigSection(
                                    settings = settings,
                                    onUpdateDefaultLens = onUpdateDefaultLens,
                                    onUpdateCaptureResolution = onUpdateCaptureResolution,
                                    onUpdateLanguage = onUpdateLanguage
                                )
                                3 -> SystemPromptSection(
                                    systemPrompt = settings.systemPrompt,
                                    onUpdateSystemPrompt = onUpdateSystemPrompt
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}
