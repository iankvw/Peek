package com.iank.im.peek.wear.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
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
import androidx.wear.compose.material.RadioButton
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.scrollAway
import com.iank.im.peek.wear.R
import com.iank.im.peek.wear.ui.theme.PeekWearBackground
import com.iank.im.peek.wear.ui.theme.PeekWearError
import com.iank.im.peek.wear.ui.theme.PeekWearErrorContainer
import com.iank.im.peek.wear.ui.theme.PeekWearOnBackground
import com.iank.im.peek.wear.ui.theme.PeekWearOnPrimary
import com.iank.im.peek.wear.ui.theme.PeekWearOnSurfaceVariant
import com.iank.im.peek.wear.ui.theme.PeekWearPrimary
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainer
import com.iank.im.peek.wear.ui.theme.PeekWearSurfaceContainerHigh
import kotlinx.coroutines.delay

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun WearReportScreen(
    onReportCompleted: () -> Unit,
    viewModel: WearReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberActiveFocusRequester()

    val reasonResIds = listOf(
        R.string.report_reason_harmful,
        R.string.report_reason_hate,
        R.string.report_reason_inaccurate,
        R.string.report_reason_other
    )
    val selectedReasonText = stringResource(reasonResIds[uiState.selectedReasonIndex])

    LaunchedEffect(uiState.submitCompleted) {
        if (uiState.submitCompleted) {
            delay(900L)
            onReportCompleted()
        }
    }

    if (uiState.submitCompleted) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .background(PeekWearBackground)
                .padding(18.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = PeekWearPrimary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = stringResource(R.string.report_success_feedback),
                style = MaterialTheme.typography.body2,
                color = PeekWearOnBackground,
                textAlign = TextAlign.Center
            )
        }
        return
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
                    behavior = RotaryScrollableDefaults.behavior(listState),
                    focusRequester = focusRequester
                )
        ) {
            item(key = "report_header") {
                ListHeader {
                    Text(
                        text = stringResource(R.string.report_title),
                        style = MaterialTheme.typography.title3,
                        fontWeight = FontWeight.SemiBold,
                        color = PeekWearOnBackground,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item(key = "report_subtitle") {
                Text(
                    text = stringResource(R.string.report_subtitle),
                    style = MaterialTheme.typography.caption2,
                    color = PeekWearOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            itemsIndexed(
                items = reasonResIds,
                key = { idx, _ -> "reason_$idx" }
            ) { index, resId ->
                val isSelected = uiState.selectedReasonIndex == index
                ToggleChip(
                    checked = isSelected,
                    onCheckedChange = { viewModel.selectReason(index) },
                    label = {
                        Text(
                            text = stringResource(resId),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    toggleControl = {
                        RadioButton(selected = isSelected)
                    },
                    colors = ToggleChipDefaults.toggleChipColors(
                        checkedStartBackgroundColor = PeekWearSurfaceContainerHigh,
                        checkedEndBackgroundColor = PeekWearSurfaceContainerHigh,
                        uncheckedStartBackgroundColor = PeekWearSurfaceContainer,
                        uncheckedEndBackgroundColor = PeekWearSurfaceContainer,
                        checkedContentColor = PeekWearOnBackground,
                        uncheckedContentColor = PeekWearOnSurfaceVariant,
                        checkedToggleControlColor = PeekWearPrimary,
                        uncheckedToggleControlColor = PeekWearOnSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.submitFailed) {
                item(key = "report_failed") {
                    Text(
                        text = stringResource(R.string.report_failed_feedback),
                        style = MaterialTheme.typography.caption1,
                        color = PeekWearError,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            item(key = "submit_report_btn") {
                Chip(
                    onClick = { viewModel.submitReport(selectedReasonText) },
                    enabled = !uiState.isSubmitting,
                    label = {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                indicatorColor = PeekWearOnPrimary,
                                trackColor = PeekWearErrorContainer,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.action_report),
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = PeekWearErrorContainer,
                        contentColor = PeekWearError
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
