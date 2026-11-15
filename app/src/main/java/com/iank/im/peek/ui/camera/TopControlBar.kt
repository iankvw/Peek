package com.iank.im.peek.ui.camera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.Conversation
import com.iank.im.peek.ui.theme.CameraControlBorder
import com.iank.im.peek.ui.theme.CameraControlContent
import com.iank.im.peek.ui.theme.CameraControlSurface

@Composable
fun TopControlBar(
    presets: List<ApiPreset>,
    selectedPreset: ApiPreset?,
    activeConversation: Conversation?,
    isPresetMenuOpen: Boolean,
    hasCameraPermission: Boolean,
    captureInFlight: Int,
    onOpenDrawer: () -> Unit,
    onTogglePresetMenu: (Boolean) -> Unit,
    onSelectPreset: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchLens: () -> Unit,
    onDetachConversation: () -> Unit,
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
                        tint = CameraControlContent
                    )
                }

                Box {
                    Surface(
                        onClick = {
                            if (presets.isEmpty()) {
                                onOpenSettings()
                            } else {
                                onTogglePresetMenu(!isPresetMenuOpen)
                            }
                        },
                        color = CameraControlSurface,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, CameraControlBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                                .widthIn(max = 180.dp)
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasCameraPermission) {
                    TranslucentCircleButton(
                        onClick = onSwitchLens,
                        enabled = captureInFlight == 0,
                        contentDescription = stringResource(R.string.cd_switch_camera_lens)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FlipCameraAndroid,
                            contentDescription = stringResource(R.string.cd_switch_camera_lens),
                            tint = CameraControlContent
                        )
                    }
                }

                TranslucentCircleButton(
                    onClick = onOpenSettings,
                    contentDescription = stringResource(R.string.cd_open_settings)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.cd_open_settings),
                        tint = CameraControlContent
                    )
                }
            }
        }

        if (activeConversation != null) {
            Surface(
                color = CameraControlSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, CameraControlBorder),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.continuing_in_conversation,
                            activeConversation.title
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = CameraControlContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDetachConversation,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_clear_continuing_conversation),
                            tint = CameraControlContent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
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
        onClick = onClick,
        enabled = enabled,
        color = CameraControlSurface,
        shape = CircleShape,
        border = BorderStroke(1.dp, CameraControlBorder),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
