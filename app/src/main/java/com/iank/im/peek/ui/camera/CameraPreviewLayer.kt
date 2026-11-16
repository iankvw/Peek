package com.iank.im.peek.ui.camera

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.CameraLensOption
import com.iank.im.peek.domain.model.CaptureResolutionOption
import com.iank.im.peek.ui.common.UiText
import com.iank.im.peek.ui.theme.CameraBottomGradient
import com.iank.im.peek.ui.theme.CameraControlSurfaceStrong
import com.iank.im.peek.ui.theme.CameraTopGradient

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraPreviewLayer(
    controller: CameraSessionController,
    hasCameraPermission: Boolean,
    cameraShouldRun: Boolean,
    activeLens: CameraLensOption,
    captureResolution: CaptureResolutionOption,
    cameraErrorText: UiText?,
    onLensFallback: (CameraLensOption) -> Unit,
    onCameraError: (UiText?) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hostLifecycleOwner = LocalLifecycleOwner.current
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var hostState by remember { mutableStateOf(hostLifecycleOwner.lifecycle.currentState) }
    var rebindTrigger by remember { mutableIntStateOf(0) }
    val latestCameraShouldRun by rememberUpdatedState(cameraShouldRun)

    DisposableEffect(hostLifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, _ ->
            hostState = hostLifecycleOwner.lifecycle.currentState
            controller.gatedOwner.updateGate(hostState, latestCameraShouldRun)
        }
        hostLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            hostLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hostState, cameraShouldRun) {
        controller.gatedOwner.updateGate(hostState, cameraShouldRun)
    }

    LaunchedEffect(hasCameraPermission, rebindTrigger) {
        if (hasCameraPermission && provider == null) {
            onCameraError(null)
            runCatching { controller.acquireProvider() }
                .onSuccess { provider = it }
                .onFailure { err ->
                    onCameraError(
                        UiText.StringResource(
                            R.string.camera_error_generic,
                            err.javaClass.simpleName
                        )
                    )
                }
        }
    }

    LaunchedEffect(provider, hasCameraPermission, activeLens, captureResolution, rebindTrigger) {
        if (!hasCameraPermission) {
            controller.unbindAllOwnUseCases()
            return@LaunchedEffect
        }
        val currentProvider = provider ?: return@LaunchedEffect
        controller.bindUseCases(
            provider = currentProvider,
            requestedLens = activeLens,
            captureResolution = captureResolution,
            onLensFallback = onLensFallback,
            onCameraError = onCameraError
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        setOnTouchListener { v, event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                controller.focusAtPoint(this, event.x, event.y)
                                v.performClick()
                            }
                            true
                        }
                        controller.attachPreviewView(this)
                    }
                },
                update = { previewView ->
                    controller.attachPreviewView(previewView)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            GalleryOnlyPlaceholder(
                onRequestPermission = onRequestPermission,
                onOpenAppSettings = onOpenAppSettings,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Subtle top and bottom atmospheric gradient scrims so translucent controls stay legible
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to CameraTopGradient,
                        0.22f to Color.Transparent,
                        0.68f to Color.Transparent,
                        1.0f to CameraBottomGradient
                    )
                )
        )

        if (hasCameraPermission && cameraErrorText != null) {
            Surface(
                color = CameraControlSurfaceStrong,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = cameraErrorText.asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    FilledTonalButton(onClick = { rebindTrigger++ }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(text = stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryOnlyPlaceholder(
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = 32.dp)
    ) {
        Surface(
            color = CameraControlSurfaceStrong,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.NoPhotography,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.camera_gallery_only_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.camera_permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.camera_permission_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC4C7C5),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRequestPermission) {
                        Text(text = stringResource(R.string.action_grant_permission))
                    }
                    OutlinedButton(onClick = onOpenAppSettings) {
                        Text(
                            text = stringResource(R.string.action_open_settings),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
