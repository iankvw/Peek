package com.iank.im.peek.ui.camera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.CameraLensOption
import com.iank.im.peek.domain.model.Conversation
import com.iank.im.peek.ui.theme.CameraControlBorder
import com.iank.im.peek.ui.theme.CameraControlContent
import com.iank.im.peek.ui.theme.CameraControlSurface

private val TOP_CONTROL_HEIGHT = 40.dp

@Composable
fun TopControlBar(
    presets: List<ApiPreset>,
    selectedPreset: ApiPreset?,
    activeConversation: Conversation?,
    activeLens: CameraLensOption,
    isPresetMenuOpen: Boolean,
    hasCameraPermission: Boolean,
    captureInFlight: Int,
    onOpenDrawer: () -> Unit,
    onTogglePresetMenu: (Boolean) -> Unit,
    onSelectPreset: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectLens: (CameraLensOption) -> Unit,
    onSwitchLens: () -> Unit,
    onReturnToActiveChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                TranslucentCircleButton(
                    onClick = onOpenDrawer,
                    contentDescription = stringResource(R.string.cd_open_drawer)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = stringResource(R.string.cd_open_drawer),
                        tint = CameraControlContent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    Surface(
                        color = CameraControlSurface,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, CameraControlBorder),
                        modifier = Modifier
                            .height(TOP_CONTROL_HEIGHT)
                            .clip(CircleShape)
                            .clickable {
                                if (presets.isEmpty()) {
                                    onOpenSettings()
                                } else {
                                    onTogglePresetMenu(!isPresetMenuOpen)
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 13.dp)
                                .widthIn(max = 160.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = stringResource(R.string.cd_select_preset),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedPreset?.alias?.ifBlank { selectedPreset.modelId }
                                    ?: stringResource(R.string.preset_none_configured),
                                style = MaterialTheme.typography.labelLarge,
                                color = CameraControlContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (presets.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = CameraControlContent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = isPresetMenuOpen && presets.isNotEmpty(),
                        onDismissRequest = { onTogglePresetMenu(false) }
                    ) {
                        presets.forEach { preset ->
                            val isSelected = preset.id == selectedPreset?.id
                            DropdownMenuItem(
                                text = {
                                    Column {
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
                                },
                                trailingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectPreset(preset.id)
                                    onTogglePresetMenu(false)
                                }
                            )
                        }
                    }
                }
            }

            if (hasCameraPermission) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!activeLens.isFront) {
                        Surface(
                            color = CameraControlSurface,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, CameraControlBorder),
                            modifier = Modifier.height(TOP_CONTROL_HEIGHT)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                CameraLensOption.rearLenses.forEach { lensOption ->
                                    val selected = activeLens == lensOption
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    Color.Transparent
                                                }
                                            )
                                            .clickable(enabled = captureInFlight == 0) {
                                                if (!selected) {
                                                    onSelectLens(lensOption)
                                                }
                                            }
                                            .padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = lensOption.badgeLabel,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                CameraControlContent
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TranslucentCircleButton(
                        onClick = onSwitchLens,
                        enabled = captureInFlight == 0,
                        contentDescription = stringResource(R.string.cd_switch_camera_lens)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FlipCameraAndroid,
                            contentDescription = stringResource(R.string.cd_switch_camera_lens),
                            tint = CameraControlContent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (activeConversation != null) {
            Surface(
                color = CameraControlSurface,
                shape = CircleShape,
                border = BorderStroke(1.dp, CameraControlBorder),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onReturnToActiveChat)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_return_to_chat),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            R.string.continuing_in_conversation,
                            activeConversation.title
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = CameraControlContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 220.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_return_to_chat),
                        tint = CameraControlContent.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslucentCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Surface(
        color = CameraControlSurface,
        shape = CircleShape,
        border = BorderStroke(1.dp, CameraControlBorder),
        modifier = Modifier
            .size(TOP_CONTROL_HEIGHT)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
