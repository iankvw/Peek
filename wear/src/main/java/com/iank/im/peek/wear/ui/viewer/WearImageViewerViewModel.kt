package com.iank.im.peek.wear.ui.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iank.im.peek.wear.data.bridge.WearPhoneBridgeClient
import com.iank.im.peek.wear.data.cache.WearImageCache
import com.iank.im.peek.wear.data.datastore.WearSettingsDataStore
import com.iank.im.peek.wear.domain.model.WearImageQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class WearImageViewerUiState(
    val previewFile: File? = null,
    val fullImageFile: File? = null,
    val quality: WearImageQuality = WearImageQuality.BALANCED_2000,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false
)

@HiltViewModel
class WearImageViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bridgeClient: WearPhoneBridgeClient,
    private val imageCache: WearImageCache,
    private val settingsDataStore: WearSettingsDataStore
) : ViewModel() {

    private val attachmentId: String = checkNotNull(savedStateHandle.get<String>("attachmentId"))

    private val _uiState = MutableStateFlow(WearImageViewerUiState())
    val uiState: StateFlow<WearImageViewerUiState> = _uiState.asStateFlow()

    init {
        loadFullImage()
    }

    fun loadFullImage() {
        viewModelScope.launch {
            val quality = settingsDataStore.getImageQuality()
            val preview = imageCache.getBestAvailablePreview(attachmentId, quality)
            val alreadyCachedFull = imageCache.getFullImageFile(attachmentId, quality)
            if (alreadyCachedFull != null) {
                _uiState.update {
                    it.copy(
                        previewFile = preview,
                        fullImageFile = alreadyCachedFull,
                        quality = quality,
                        isLoading = false,
                        loadFailed = false
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    previewFile = preview,
                    quality = quality,
                    isLoading = true,
                    loadFailed = false
                )
            }

            // Priority request: bypasses background thumbnail semaphore
            val fetched = bridgeClient.requestFullImage(attachmentId, quality)
            _uiState.update { state ->
                state.copy(
                    fullImageFile = fetched,
                    isLoading = false,
                    loadFailed = fetched == null
                )
            }
        }
    }
}
