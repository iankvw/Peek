package com.iank.im.peek.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.AppLanguage
import com.iank.im.peek.domain.model.AppSettings
import com.iank.im.peek.domain.model.CameraLensOption
import com.iank.im.peek.domain.model.CaptureResolutionOption

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CameraConfigSection(
    settings: AppSettings,
    onUpdateDefaultLens: (CameraLensOption) -> Unit,
    onUpdateCaptureResolution: (CaptureResolutionOption) -> Unit,
    onUpdateLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_camera_header),
            style = MaterialTheme.typography.titleMedium
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.settings_default_lens_label),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = settings.defaultCameraLens == CameraLensOption.BACK_ULTRA_WIDE,
                    onClick = { onUpdateDefaultLens(CameraLensOption.BACK_ULTRA_WIDE) },
                    label = { Text(stringResource(R.string.camera_lens_back_ultra_wide)) }
                )
                FilterChip(
                    selected = settings.defaultCameraLens == CameraLensOption.BACK,
                    onClick = { onUpdateDefaultLens(CameraLensOption.BACK) },
                    label = { Text(stringResource(R.string.camera_lens_back)) }
                )
                FilterChip(
                    selected = settings.defaultCameraLens == CameraLensOption.BACK_TELEPHOTO,
                    onClick = { onUpdateDefaultLens(CameraLensOption.BACK_TELEPHOTO) },
                    label = { Text(stringResource(R.string.camera_lens_back_telephoto)) }
                )
                FilterChip(
                    selected = settings.defaultCameraLens == CameraLensOption.FRONT,
                    onClick = { onUpdateDefaultLens(CameraLensOption.FRONT) },
                    label = { Text(stringResource(R.string.camera_lens_front)) }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.settings_capture_resolution_label),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = settings.captureResolution == CaptureResolutionOption.MAX,
                    onClick = { onUpdateCaptureResolution(CaptureResolutionOption.MAX) },
                    label = { Text(stringResource(R.string.capture_res_max)) }
                )
                FilterChip(
                    selected = settings.captureResolution == CaptureResolutionOption.HIGH,
                    onClick = { onUpdateCaptureResolution(CaptureResolutionOption.HIGH) },
                    label = { Text(stringResource(R.string.capture_res_high)) }
                )
                FilterChip(
                    selected = settings.captureResolution == CaptureResolutionOption.BALANCED,
                    onClick = { onUpdateCaptureResolution(CaptureResolutionOption.BALANCED) },
                    label = { Text(stringResource(R.string.capture_res_balanced)) }
                )
                FilterChip(
                    selected = settings.captureResolution == CaptureResolutionOption.STANDARD,
                    onClick = { onUpdateCaptureResolution(CaptureResolutionOption.STANDARD) },
                    label = { Text(stringResource(R.string.capture_res_standard)) }
                )
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.settings_language_header),
            style = MaterialTheme.typography.titleMedium
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.settings_language_label),
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.appLanguage == AppLanguage.SYSTEM,
                    onClick = { onUpdateLanguage(AppLanguage.SYSTEM) },
                    label = { Text(stringResource(R.string.language_system)) }
                )
                FilterChip(
                    selected = settings.appLanguage == AppLanguage.EN_US,
                    onClick = { onUpdateLanguage(AppLanguage.EN_US) },
                    label = { Text(stringResource(R.string.language_en_us)) }
                )
                FilterChip(
                    selected = settings.appLanguage == AppLanguage.KO,
                    onClick = { onUpdateLanguage(AppLanguage.KO) },
                    label = { Text(stringResource(R.string.language_ko)) }
                )
            }
        }
    }
}
