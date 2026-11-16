package com.iank.im.peek.wear.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material.scrollAway
import com.iank.im.peek.wear.R
import com.iank.im.peek.wear.domain.model.WearConversationItem
import com.iank.im.peek.wear.ui.common.WearUiHelpers
import com.iank.im.peek.wear.ui.theme.PeekWearBackground
import com.iank.im.peek.wear.ui.theme.PeekWearError
import com.iank.im.peek.wear.ui.theme.PeekWearErrorContainer
import com.iank.im.peek.wear.ui.theme.PeekWearOnBackground
import com.iank.im.peek.wear.ui.theme.PeekWearOnPrimary
import com.iank.im.peek.wear.ui.theme.PeekWearOnSurfaceVariant
import com.iank.im.peek.wear.ui.theme.PeekWearPrimary
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainer
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainerHigh

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearHomeScreen(
    onOpenNewChat: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: WearHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberActiveFocusRequester()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshConversations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                    behavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
                    focusRequester = focusRequester
                )
        ) {
            // 1. Top App Title
            item(key = "header_title") {
                ListHeader {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(PeekWearPrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.title2,
                            fontWeight = FontWeight.Bold,
                            color = PeekWearOnBackground
                        )
                    }
                }
            }

            // 2. New Conversation Button
            item(key = "new_conversation_btn") {
                Chip(
                    onClick = {
                        if (!uiState.hasConfiguredTarget && uiState.isConnected) {
                            viewModel.showNoPresetNotice()
                        } else {
                            onOpenNewChat()
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.home_new_conversation),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = PeekWearPrimary,
                        contentColor = PeekWearOnPrimary,
                        iconColor = PeekWearOnPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Chat List / Connection / Empty States
            if (uiState.isLoadingInitial) {
                item(key = "loading_initial") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(
                            indicatorColor = PeekWearPrimary,
                            trackColor = PeekWearSurfaceContainerHigh,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else if (!uiState.isConnected && uiState.conversations.isEmpty()) {
                item(key = "disconnected_state") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(PeekWearSurfaceContainer)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_phone_disconnected),
                            style = MaterialTheme.typography.body2,
                            color = PeekWearOnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Chip(
                            onClick = viewModel::refreshConversations,
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
            } else if (uiState.conversations.isEmpty()) {
                item(key = "empty_conversations") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(PeekWearSurfaceContainer)
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_empty_conversations),
                            style = MaterialTheme.typography.body2,
                            color = PeekWearOnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(
                    items = uiState.conversations,
                    key = { it.id }
                ) { item ->
                    WearConversationCard(
                        item = item,
                        subtitle = WearUiHelpers.formatRelativeTime(context, item.updatedAt),
                        onClick = { onOpenConversation(item.id) },
                        onLongClick = { viewModel.requestDeleteConversation(item) }
                    )
                }
            }

            // 4. Conditional "Load More" button (only when hasMore == true)
            if (uiState.hasMore) {
                item(key = "load_more_btn") {
                    Chip(
                        onClick = viewModel::loadMoreConversations,
                        enabled = !uiState.isLoadingMore,
                        label = {
                            if (uiState.isLoadingMore) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CircularProgressIndicator(
                                        indicatorColor = PeekWearPrimary,
                                        trackColor = PeekWearSurfaceContainer,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.action_load_more),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = PeekWearSurfaceContainerHigh,
                            contentColor = PeekWearOnBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 5. Settings Button at bottom
            item(key = "settings_btn") {
                Chip(
                    onClick = onOpenSettings,
                    label = {
                        Text(
                            text = stringResource(R.string.home_settings),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
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
        }
    }

    // Delete Confirmation Dialog
    val pendingDelete = uiState.pendingDeleteConversation
    Dialog(
        showDialog = pendingDelete != null,
        onDismissRequest = viewModel::dismissDeleteDialog
    ) {
        if (pendingDelete != null) {
            val dialogScroll = rememberScalingLazyListState()
            val dialogFocus = rememberActiveFocusRequester()
            ScalingLazyColumn(
                state = dialogScroll,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .background(PeekWearBackground)
                    .padding(horizontal = 12.dp)
                    .rotaryScrollable(
                        behavior = RotaryScrollableDefaults.behavior(dialogScroll),
                        focusRequester = dialogFocus
                    )
            ) {
                item {
                    Text(
                        text = stringResource(R.string.home_delete_dialog_title),
                        style = MaterialTheme.typography.title3,
                        fontWeight = FontWeight.SemiBold,
                        color = PeekWearOnBackground,
                        textAlign = TextAlign.Center
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.home_delete_dialog_message, pendingDelete.title),
                        style = MaterialTheme.typography.body2,
                        color = PeekWearOnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item {
                    Chip(
                        onClick = viewModel::confirmDeleteConversation,
                        label = {
                            Text(
                                text = stringResource(R.string.action_delete),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = PeekWearErrorContainer,
                            contentColor = PeekWearError
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Chip(
                        onClick = viewModel::dismissDeleteDialog,
                        label = {
                            Text(
                                text = stringResource(R.string.action_cancel),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = PeekWearSurfaceContainerHigh,
                            contentColor = PeekWearOnBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // No Preset Configured Hint Dialog
    Dialog(
        showDialog = uiState.showNoPresetNotice,
        onDismissRequest = viewModel::dismissNoPresetNotice
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(PeekWearBackground)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.home_no_preset_hint),
                style = MaterialTheme.typography.body2,
                color = PeekWearOnBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Chip(
                onClick = viewModel::dismissNoPresetNotice,
                label = { Text(stringResource(R.string.action_confirm)) },
                colors = ChipDefaults.chipColors(
                    backgroundColor = PeekWearPrimary,
                    contentColor = PeekWearOnPrimary
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WearConversationCard(
    item: WearConversationItem,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PeekWearSurfaceContainer)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.body1,
            fontWeight = FontWeight.Medium,
            color = PeekWearOnBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.caption2,
                color = PeekWearOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.isGenerating) {
                CircularProgressIndicator(
                    indicatorColor = PeekWearPrimary,
                    trackColor = PeekWearSurfaceContainerHigh,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
