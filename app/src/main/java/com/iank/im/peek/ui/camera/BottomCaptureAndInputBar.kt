package com.iank.im.peek.ui.camera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.PendingAttachment
import com.iank.im.peek.ui.theme.CameraControlBorder
import com.iank.im.peek.ui.theme.CameraControlContent
import com.iank.im.peek.ui.theme.CameraControlSurface
import com.iank.im.peek.ui.theme.CameraControlSurfaceStrong
import java.io.File

@Composable
fun BottomCaptureAndInputBar(
    pendingAttachments: List<PendingAttachment>,
    resolveFile: (String) -> File?,
    inputText: String,
    hasCameraPermission: Boolean,
    captureInFlight: Int,
    canSend: Boolean,
    onInputChanged: (String) -> Unit,
    onRemovePending: (String) -> Unit,
    onPickFromGallery: () -> Unit,
    onCapturePhoto: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (pendingAttachments.isNotEmpty()) {
            PendingAttachmentsStrip(
                pendingAttachments = pendingAttachments,
                resolveFile = resolveFile,
                onRemovePending = onRemovePending
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                onClick = onPickFromGallery,
                color = CameraControlSurface,
                shape = CircleShape,
                border = BorderStroke(1.dp, CameraControlBorder),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = stringResource(R.string.cd_pick_from_gallery),
                        tint = CameraControlContent
                    )
                }
            }

            if (hasCameraPermission) {
                ShutterButton(
                    captureInFlight = captureInFlight > 0,
                    onClick = onCapturePhoto
                )
            } else {
                Spacer(modifier = Modifier.size(72.dp))
            }

            // Visual balance spacer on right side
            Spacer(modifier = Modifier.size(50.dp))
        }

        Surface(
            color = CameraControlSurfaceStrong,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, CameraControlBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChanged,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.input_hint_ask_about_scene),
                            color = Color(0xFFC4C7C5),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    onClick = onSend,
                    enabled = canSend,
                    color = if (canSend) MaterialTheme.colorScheme.primary else Color(0x40FFFFFF),
                    shape = CircleShape,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (captureInFlight > 0) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.cd_send_message),
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary else Color(0x99FFFFFF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PendingAttachmentsStrip(
    pendingAttachments: List<PendingAttachment>,
    resolveFile: (String) -> File?,
    onRemovePending: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(pendingAttachments, key = { it.id }) { item ->
            val file = resolveFile(item.stagingThumbnailRelPath)
                ?: resolveFile(item.stagingOriginalRelPath)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, CameraControlBorder, RoundedCornerShape(14.dp))
                    .background(CameraControlSurfaceStrong)
            ) {
                AsyncImage(
                    model = file,
                    contentDescription = stringResource(R.string.cd_pending_thumbnail),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    onClick = { onRemovePending(item.id) },
                    color = Color(0xCC000000),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_remove_pending_attachment),
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(
    captureInFlight: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(3.dp, Color.White),
        modifier = Modifier.size(72.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
                .clip(CircleShape)
                .background(if (captureInFlight) Color(0xFFFFE082) else Color.White)
        ) {
            if (captureInFlight) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                    color = Color.Black
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = stringResource(R.string.cd_capture_photo),
                    tint = Color(0xFF131314),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
