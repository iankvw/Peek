package com.iank.im.peek.wear.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.RadioButton
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material.scrollAway
import com.iank.im.peek.wear.R
import com.iank.im.peek.wear.domain.model.WearImageQuality
import com.iank.im.peek.wear.domain.model.WearInputMode
import com.iank.im.peek.wear.ui.theme.PeekWearBackground
import com.iank.im.peek.wear.ui.theme.PeekWearError
import com.iank.im.peek.wear.ui.theme.PeekWearOnBackground
import com.iank.im.peek.wear.ui.theme.PeekWearOnPrimary
import com.iank.im.peek.wear.ui.theme.PeekWearOnSurfaceVariant
import com.iank.im.peek.wear.ui.theme.PeekWearPrimary
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainer
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainerHigh

@Composable
fun WearSettingsScreen(
    onOpenQualitySettings: () -> Unit,
    onOpenTargetSettings: () -> Unit,
    onOpenInputSettings: () -> Unit,
    viewModel: WearSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val privacyUrl = stringResource(R.string.privacy_policy_url)
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberActiveFocusRequester()

    Scaffold(
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(PeekWearBackground)
                .rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(listState),
                    focusRequester = focusRequester
                )
        ) {
            item(key = "settings_header") {
                ListHeader {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.title3,
                        fontWeight = FontWeight.SemiBold,
                        color = PeekWearOnBackground
                    )
                }
            }

            // 1. Photo View Quality
            item(key = "item_quality") {
                Chip(
                    onClick = onOpenQualitySettings,
                    label = {
                        Text(
                            text = stringResource(R.string.settings_photo_quality),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = qualityLabel(uiState.imageQuality),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.HighQuality,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = PeekWearSurfaceContainer,
                        contentColor = PeekWearOnBackground,
                        secondaryContentColor = PeekWearOnSurfaceVariant,
                        iconColor = PeekWearPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. Default API Preset / Group (Two-way synced with phone)
            item(key = "item_target") {
                Chip(
                    onClick = onOpenTargetSettings,
                    label = {
                        Text(
                            text = stringResource(R.string.settings_default_target),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = uiState.selectedTarget?.displayName
                                ?: stringResource(R.string.chat_target_unconfigured),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = PeekWearSurfaceContainer,
                        contentColor = PeekWearOnBackground,
                        secondaryContentColor = PeekWearOnSurfaceVariant,
                        iconColor = PeekWearPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Default Input Mode (Keyboard default / Voice)
            item(key = "item_input") {
                Chip(
                    onClick = onOpenInputSettings,
                    label = {
                        Text(
                            text = stringResource(R.string.settings_default_input),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = inputModeLabel(uiState.inputMode),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Keyboard,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = PeekWearSurfaceContainer,
                        contentColor = PeekWearOnBackground,
                        secondaryContentColor = PeekWearOnSurfaceVariant,
                        iconColor = PeekWearPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. Privacy Policy (RemoteActivityHelper -> phone browser)
            item(key = "item_privacy") {
                Chip(
                    onClick = { viewModel.openPrivacyPolicyOnPhone(context, privacyUrl) },
                    label = {
                        Text(
                            text = stringResource(R.string.settings_privacy_policy),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = PeekWearSurfaceContainerHigh,
                        contentColor = PeekWearOnBackground,
                        iconColor = PeekWearOnSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Trademark compliance notice
            item(key = "trademark_notice") {
                Text(
                    text = stringResource(R.string.settings_trademark_notice),
                    style = MaterialTheme.typography.caption2.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = PeekWearOnSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }

    Dialog(
        showDialog = uiState.privacyFeedback != PrivacyLaunchFeedback.NONE,
        onDismissRequest = viewModel::dismissPrivacyFeedback
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(PeekWearBackground)
                .padding(16.dp)
        ) {
            if (uiState.privacyFeedback == PrivacyLaunchFeedback.OPENED_ON_PHONE) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = PeekWearPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.privacy_opened_on_phone),
                    style = MaterialTheme.typography.body2,
                    color = PeekWearOnBackground,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = stringResource(R.string.privacy_open_failed),
                    style = MaterialTheme.typography.body2,
                    color = PeekWearOnBackground,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Chip(
                onClick = viewModel::dismissPrivacyFeedback,
                label = { Text(stringResource(R.string.action_confirm)) },
                colors = ChipDefaults.chipColors(
                    backgroundColor = PeekWearPrimary,
                    contentColor = PeekWearOnPrimary
                )
            )
        }
    }
}

@Composable
fun WearQualitySettingsScreen(
    onSelected: () -> Unit,
    viewModel: WearSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberActiveFocusRequester()

    Scaffold(
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(PeekWearBackground)
                .rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(listState),
                    focusRequester = focusRequester
                )
        ) {
            item(key = "quality_header") {
                ListHeader {
                    Text(
                        text = stringResource(R.string.settings_photo_quality),
                        style = MaterialTheme.typography.title3,
                        color = PeekWearOnBackground,
                        textAlign = TextAlign.Center
                    )
                }
            }
            items(
                items = WearImageQuality.entries,
                key = { it.name }
            ) { quality ->
                val isSelected = uiState.imageQuality == quality
                WearRadioOptionRow(
                    label = qualityLabel(quality),
                    secondaryLabel = null,
                    selected = isSelected,
                    onClick = {
                        viewModel.selectImageQuality(quality)
                        onSelected()
                    }
                )
            }
        }
    }
}

@Composable
fun WearTargetSettingsScreen(
    onSelected: () -> Unit,
    viewModel: WearSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberActiveFocusRequester()
    val groupBadge = stringResource(R.string.settings_group_badge)

    Scaffold(
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(PeekWearBackground)
                .rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(listState),
                    focusRequester = focusRequester
                )
        ) {
            item(key = "target_header") {
                ListHeader {
                    Text(
                        text = stringResource(R.string.settings_default_target),
                        style = MaterialTheme.typography.title3,
                        color = PeekWearOnBackground,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (uiState.targetUpdateError) {
                item(key = "target_error") {
                    Text(
                        text = stringResource(R.string.settings_target_update_failed),
                        style = MaterialTheme.typography.caption1,
                        color = PeekWearError,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (uiState.targets.isEmpty()) {
                item(key = "no_targets") {
                    Text(
                        text = stringResource(R.string.settings_no_targets),
                        style = MaterialTheme.typography.body2,
                        color = PeekWearOnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                items(
                    items = uiState.targets,
                    key = { it.id }
                ) { target ->
                    val isSelected = target.id == uiState.selectedTargetId
                    val title = if (target.isGroup) {
                        "[$groupBadge] ${target.displayName}"
                    } else {
                        target.displayName
                    }
                    WearRadioOptionRow(
                        label = title,
                        secondaryLabel = target.subtitle,
                        selected = isSelected,
                        onClick = {
                            viewModel.selectDefaultTarget(target.id, onSelected)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WearInputSettingsScreen(
    onSelected: () -> Unit,
    viewModel: WearSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberActiveFocusRequester()

    Scaffold(
        timeText = { TimeText(modifier = Modifier.scrollAway(listState)) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(PeekWearBackground)
                .rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(listState),
                    focusRequester = focusRequester
                )
        ) {
            item(key = "input_header") {
                ListHeader {
                    Text(
                        text = stringResource(R.string.settings_default_input),
                        style = MaterialTheme.typography.title3,
                        color = PeekWearOnBackground,
                        textAlign = TextAlign.Center
                    )
                }
            }
            items(
                items = WearInputMode.entries,
                key = { it.name }
            ) { mode ->
                val isSelected = uiState.inputMode == mode
                WearRadioOptionRow(
                    label = inputModeLabel(mode),
                    secondaryLabel = null,
                    selected = isSelected,
                    onClick = {
                        viewModel.selectInputMode(mode)
                        onSelected()
                    }
                )
            }
        }
    }
}

@Composable
private fun WearRadioOptionRow(
    label: String,
    secondaryLabel: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    ToggleChip(
        checked = selected,
        onCheckedChange = { onClick() },
        label = {
            Text(
                text = label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = secondaryLabel?.let { sub ->
            {
                Text(
                    text = sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        toggleControl = {
            RadioButton(selected = selected)
        },
        colors = ToggleChipDefaults.toggleChipColors(
            checkedStartBackgroundColor = PeekWearSurfaceContainerHigh,
            checkedEndBackgroundColor = PeekWearSurfaceContainerHigh,
            uncheckedStartBackgroundColor = PeekWearSurfaceContainer,
            uncheckedEndBackgroundColor = PeekWearSurfaceContainer,
            checkedContentColor = PeekWearOnBackground,
            uncheckedContentColor = PeekWearOnSurfaceVariant,
            checkedSecondaryContentColor = PeekWearOnSurfaceVariant,
            uncheckedSecondaryContentColor = PeekWearOnSurfaceVariant,
            checkedToggleControlColor = PeekWearPrimary,
            uncheckedToggleControlColor = PeekWearOnSurfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun qualityLabel(quality: WearImageQuality): String = when (quality) {
    WearImageQuality.ORIGINAL -> stringResource(R.string.quality_original)
    WearImageQuality.HIGH_4000 -> stringResource(R.string.quality_high_4000)
    WearImageQuality.BALANCED_2000 -> stringResource(R.string.quality_balanced_2000)
    WearImageQuality.STANDARD_1000 -> stringResource(R.string.quality_standard_1000)
}

@Composable
private fun inputModeLabel(mode: WearInputMode): String = when (mode) {
    WearInputMode.TEXT_KEYBOARD -> stringResource(R.string.input_mode_keyboard)
    WearInputMode.VOICE_FIRST -> stringResource(R.string.input_mode_voice)
}
