package com.iank.im.peek.wear.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil3.compose.AsyncImage
import com.iank.im.peek.wear.R
import com.iank.im.peek.wear.ui.theme.PeekWearBackground
import com.iank.im.peek.wear.ui.theme.PeekWearError
import com.iank.im.peek.wear.ui.theme.PeekWearOnBackground
import com.iank.im.peek.wear.ui.theme.PeekWearPrimary
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainer
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainerHigh

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun WearImageViewerScreen(
    onClose: () -> Unit,
    viewModel: WearImageViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = rememberActiveFocusRequester()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(candidate: Offset, currentScale: Float): Offset {
        if (currentScale <= 1f || viewportSize == IntSize.Zero) return Offset.Zero
        val maxX = (viewportSize.width * (currentScale - 1f)) / 2f
        val maxY = (viewportSize.height * (currentScale - 1f)) / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY)
        )
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val displayFile = uiState.fullImageFile ?: uiState.previewFile

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PeekWearBackground)
            .onSizeChanged { viewportSize = it }
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                val delta = if (event.verticalScrollPixels > 0f) 0.2f else -0.2f
                val nextScale = (scale + delta).coerceIn(1f, 5f)
                scale = nextScale
                offset = clampOffset(offset, nextScale)
                true
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        val nextScale = if (scale > 1.25f) 1f else 2.5f
                        scale = nextScale
                        offset = if (nextScale == 1f) Offset.Zero else clampOffset(offset, nextScale)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    val nextOffset = if (nextScale > 1.01f) {
                        clampOffset(offset + pan, nextScale)
                    } else {
                        Offset.Zero
                    }
                    scale = nextScale
                    offset = nextOffset
                }
            }
    ) {
        if (displayFile != null) {
            AsyncImage(
                model = displayFile,
                contentDescription = stringResource(R.string.cd_message_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (uiState.fullImageFile == null) 0.55f else 1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }

        if (uiState.isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x99000000), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                CircularProgressIndicator(
                    indicatorColor = PeekWearPrimary,
                    trackColor = PeekWearSurfaceContainerHigh,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.viewer_loading_image),
                    style = MaterialTheme.typography.caption2,
                    color = PeekWearOnBackground
                )
            }
        } else if (uiState.loadFailed && uiState.fullImageFile == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(18.dp)
            ) {
                Text(
                    text = stringResource(R.string.viewer_load_failed),
                    style = MaterialTheme.typography.body2,
                    color = PeekWearError,
                    textAlign = TextAlign.Center
                )
                Chip(
                    onClick = viewModel::loadFullImage,
                    label = { Text(stringResource(R.string.action_retry)) },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = PeekWearSurfaceContainerHigh,
                        contentColor = PeekWearOnBackground
                    )
                )
            }
        }

        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = PeekWearSurfaceContainer.copy(alpha = 0.85f),
                contentColor = PeekWearOnBackground
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .size(36.dp)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_close_image_viewer),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
