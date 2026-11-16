package com.iank.im.peek.wear.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iank.im.peek.wear.data.bridge.WearPhoneBridgeClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WearReportUiState(
    val selectedReasonIndex: Int = 0,
    val isSubmitting: Boolean = false,
    val submitCompleted: Boolean = false,
    val submitFailed: Boolean = false
)

@HiltViewModel
class WearReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bridgeClient: WearPhoneBridgeClient
) : ViewModel() {

    private val messageId: String = checkNotNull(savedStateHandle.get<String>("messageId"))

    private val _uiState = MutableStateFlow(WearReportUiState())
    val uiState: StateFlow<WearReportUiState> = _uiState.asStateFlow()

    fun selectReason(index: Int) {
        _uiState.update { it.copy(selectedReasonIndex = index, submitFailed = false) }
    }

    fun submitReport(reasonText: String) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitFailed = false) }
            val result = bridgeClient.reportMessage(messageId = messageId, reason = reasonText)
            if (result.success) {
                _uiState.update { it.copy(isSubmitting = false, submitCompleted = true) }
            } else {
                _uiState.update { it.copy(isSubmitting = false, submitFailed = true) }
            }
        }
    }
}
