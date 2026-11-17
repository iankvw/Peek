package com.iank.im.peek.wear.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.concurrent.futures.await
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.iank.im.peek.wear.data.bridge.WearPhoneBridgeClient
import com.iank.im.peek.wear.data.datastore.WearSettingsDataStore
import com.iank.im.peek.wear.domain.model.WearImageQuality
import com.iank.im.peek.wear.domain.model.WearInputMode
import com.iank.im.peek.wear.domain.model.WearTargetItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

enum class PrivacyLaunchFeedback {
    NONE,
    OPENED_ON_PHONE,
    FAILED
}

data class WearSettingsUiState(
    val imageQuality: WearImageQuality = WearImageQuality.BALANCED_2000,
    val inputMode: WearInputMode = WearInputMode.TEXT_KEYBOARD,
    val targets: List<WearTargetItem> = emptyList(),
    val selectedTargetId: String? = null,
    val isUpdatingTarget: Boolean = false,
    val targetUpdateError: Boolean = false,
    val privacyFeedback: PrivacyLaunchFeedback = PrivacyLaunchFeedback.NONE
) {
    val selectedTarget: WearTargetItem?
        get() = targets.firstOrNull { it.id == selectedTargetId } ?: targets.firstOrNull()
}

@HiltViewModel
class WearSettingsViewModel @Inject constructor(
    private val settingsDataStore: WearSettingsDataStore,
    private val bridgeClient: WearPhoneBridgeClient
) : ViewModel() {

    val defaultInputMethod: StateFlow<WearInputMode> = settingsDataStore.defaultInputMethod

    private val _uiState = MutableStateFlow(
        WearSettingsUiState(
            imageQuality = settingsDataStore.imageQuality.value,
            inputMode = settingsDataStore.defaultInputMethod.value,
            targets = bridgeClient.syncedConfig.value.targets,
            selectedTargetId = bridgeClient.syncedConfig.value.selectedTargetId
        )
    )
    val uiState: StateFlow<WearSettingsUiState> = _uiState.asStateFlow()

    private var isUpdatingLocalPreference = false

    init {
        viewModelScope.launch {
            bridgeClient.refreshInitialSyncedConfig()
        }
        viewModelScope.launch {
            combine(
                settingsDataStore.imageQuality,
                settingsDataStore.defaultInputMethod,
                bridgeClient.syncedConfig
            ) { quality, inputMode, syncedConfig ->
                Triple(quality, inputMode, syncedConfig)
            }
                .distinctUntilChanged()
                .collect { (quality, inputMode, syncedConfig) ->
                    _uiState.update { state ->
                        state.copy(
                            imageQuality = quality,
                            inputMode = inputMode,
                            targets = syncedConfig.targets,
                            selectedTargetId = syncedConfig.selectedTargetId
                        )
                    }
                }
        }
    }

    fun selectImageQuality(
        quality: WearImageQuality,
        onCompleted: (() -> Unit)? = null
    ) {
        if (isUpdatingLocalPreference) return
        isUpdatingLocalPreference = true
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(imageQuality = quality) }
                settingsDataStore.setImageQuality(quality)
                onCompleted?.invoke()
            } finally {
                isUpdatingLocalPreference = false
            }
        }
    }

    fun selectInputMode(
        mode: WearInputMode,
        onCompleted: (() -> Unit)? = null
    ) {
        if (isUpdatingLocalPreference) return
        isUpdatingLocalPreference = true
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(inputMode = mode) }
                settingsDataStore.setDefaultInputMethod(mode)
                onCompleted?.invoke()
            } finally {
                isUpdatingLocalPreference = false
            }
        }
    }

    fun toggleInputMode(onCompleted: (() -> Unit)? = null) {
        viewModelScope.launch {
            val next = settingsDataStore.toggleDefaultInputMethod()
            _uiState.update { it.copy(inputMode = next) }
            onCompleted?.invoke()
        }
    }

    fun selectDefaultTarget(targetId: String, onCompleted: () -> Unit) {
        if (_uiState.value.isUpdatingTarget) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingTarget = true, targetUpdateError = false) }
            val result = bridgeClient.selectDefaultTarget(targetId)
            if (result.success) {
                _uiState.update {
                    it.copy(
                        selectedTargetId = targetId,
                        isUpdatingTarget = false
                    )
                }
                onCompleted()
            } else {
                _uiState.update {
                    it.copy(
                        isUpdatingTarget = false,
                        targetUpdateError = true
                    )
                }
            }
        }
    }

    fun openPrivacyPolicyOnPhone(context: Context, url: String) {
        viewModelScope.launch {
            val remoteIntent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse(url))
            val executor = Executors.newSingleThreadExecutor()
            val helper = RemoteActivityHelper(context.applicationContext, executor)
            val result = runCatching {
                helper.startRemoteActivity(remoteIntent).await()
            }
            executor.shutdown()
            _uiState.update {
                it.copy(
                    privacyFeedback = if (result.isSuccess) {
                        PrivacyLaunchFeedback.OPENED_ON_PHONE
                    } else {
                        PrivacyLaunchFeedback.FAILED
                    }
                )
            }
        }
    }

    fun dismissPrivacyFeedback() {
        _uiState.update { it.copy(privacyFeedback = PrivacyLaunchFeedback.NONE) }
    }
}
