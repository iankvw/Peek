package com.iank.im.peek.ui.camera

import android.content.Context
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import com.iank.im.peek.R
import com.iank.im.peek.domain.model.CameraLensOption
import com.iank.im.peek.domain.model.CaptureResolutionOption
import com.iank.im.peek.ui.common.UiText
import java.io.File
import java.util.concurrent.TimeUnit

class GatedLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    init {
        // Move immediately to CREATED so disposing before ever opening camera never triggers INITIALIZED -> DESTROYED crash.
        registry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle
        get() = registry

    fun updateGate(hostState: Lifecycle.State, cameraShouldRun: Boolean) {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        if (hostState == Lifecycle.State.DESTROYED) {
            registry.currentState = Lifecycle.State.DESTROYED
            return
        }
        val desired = if (cameraShouldRun) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
        val target = minOf(hostState, desired)
        if (registry.currentState != target) {
            registry.currentState = target
        }
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

class CameraSessionController(
    private val context: Context
) {
    val gatedOwner = GatedLifecycleOwner()

    private var cameraProvider: ProcessCameraProvider? = null
    private var boundPreview: Preview? = null
    private var boundImageCapture: ImageCapture? = null
    private var boundCamera: Camera? = null
    private var cameraStateObserver: Observer<CameraState>? = null
    private var currentSurfaceProvider: Preview.SurfaceProvider? = null

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val rotation = when (orientation) {
                in 45..134 -> Surface.ROTATION_270
                in 135..224 -> Surface.ROTATION_180
                in 225..314 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            boundImageCapture?.targetRotation = rotation
        }
    }

    init {
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
    }

    suspend fun acquireProvider(): ProcessCameraProvider {
        cameraProvider?.let { return it }
        return ProcessCameraProvider.awaitInstance(context).also {
            cameraProvider = it
        }
    }

    fun attachPreviewView(previewView: PreviewView) {
        currentSurfaceProvider = previewView.surfaceProvider
        boundPreview?.surfaceProvider = currentSurfaceProvider
    }

    fun focusAtPoint(previewView: PreviewView, x: Float, y: Float) {
        val camera = boundCamera ?: return
        val factory = previewView.meteringPointFactory
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        runCatching { camera.cameraControl.startFocusAndMetering(action) }
    }

    fun bindUseCases(
        provider: ProcessCameraProvider,
        requestedLens: CameraLensOption,
        captureResolution: CaptureResolutionOption,
        onLensFallback: (CameraLensOption) -> Unit,
        onCameraError: (UiText?) -> Unit
    ) {
        unbindOwnUseCases(provider)
        onCameraError(null)

        val resolvedLens = resolveAvailableLens(provider, requestedLens)
        if (resolvedLens == null) {
            onCameraError(UiText.StringResource(R.string.camera_error_generic, "NO_CAMERA"))
            return
        }
        if (resolvedLens != requestedLens) {
            onLensFallback(resolvedLens)
        }

        val selector = resolvedLens.toCameraSelector()
        val preview = Preview.Builder().build().apply {
            surfaceProvider = currentSurfaceProvider
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(buildResolutionSelector(captureResolution))
            .build()

        val bindResult = runCatching {
            provider.bindToLifecycle(gatedOwner, selector, preview, imageCapture)
        }

        bindResult.onSuccess { camera ->
            boundPreview = preview
            boundImageCapture = imageCapture
            boundCamera = camera
            observeCameraState(camera, onCameraError)
        }.onFailure { throwable ->
            boundPreview = null
            boundImageCapture = null
            boundCamera = null
            onCameraError(
                UiText.StringResource(
                    R.string.camera_error_generic,
                    throwable.javaClass.simpleName
                )
            )
        }
    }

    fun capturePhoto(
        prepareTarget: () -> Pair<String, File>,
        onCaptureStarted: () -> Unit,
        onCaptureSucceeded: (id: String, stagingRelPath: String) -> Unit,
        onCaptureFailed: (stagingRelPath: String, cancelledByGate: Boolean) -> Unit
    ) {
        val imageCapture = boundImageCapture ?: return
        val (id, file) = prepareTarget()
        val stagingRelPath = "staging/${file.name}"
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        val executor = ContextCompat.getMainExecutor(context)

        onCaptureStarted()
        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onCaptureSucceeded(id, stagingRelPath)
                }

                override fun onError(exception: ImageCaptureException) {
                    val cancelledByGate =
                        exception.imageCaptureError == ImageCapture.ERROR_CAMERA_CLOSED
                    onCaptureFailed(stagingRelPath, cancelledByGate)
                }
            }
        )
    }

    fun dispose() {
        orientationListener.disable()
        clearCameraStateObserver()
        cameraProvider?.let { unbindOwnUseCases(it) }
        gatedOwner.destroy()
    }

    private fun unbindOwnUseCases(provider: ProcessCameraProvider) {
        clearCameraStateObserver()
        val toUnbind = listOfNotNull(boundPreview, boundImageCapture)
        if (toUnbind.isNotEmpty()) {
            runCatching { provider.unbind(*toUnbind.toTypedArray()) }
        }
        boundPreview = null
        boundImageCapture = null
        boundCamera = null
    }

    private fun observeCameraState(camera: Camera, onCameraError: (UiText?) -> Unit) {
        clearCameraStateObserver()
        val observer = Observer<CameraState> { state ->
            val err = state.error
            if (err == null) {
                onCameraError(null)
            } else {
                val mapped = when (err.code) {
                    CameraState.ERROR_CAMERA_IN_USE ->
                        UiText.StringResource(R.string.camera_error_in_use)
                    CameraState.ERROR_MAX_CAMERAS_IN_USE ->
                        UiText.StringResource(R.string.camera_error_max_cameras)
                    CameraState.ERROR_CAMERA_DISABLED ->
                        UiText.StringResource(R.string.camera_error_disabled_policy)
                    else ->
                        UiText.StringResource(R.string.camera_error_generic, err.code.toString())
                }
                onCameraError(mapped)
            }
        }
        cameraStateObserver = observer
        camera.cameraInfo.cameraState.observe(gatedOwner, observer)
    }

    private fun clearCameraStateObserver() {
        val prevCam = boundCamera
        val prevObs = cameraStateObserver
        if (prevCam != null && prevObs != null) {
            prevCam.cameraInfo.cameraState.removeObserver(prevObs)
        }
        cameraStateObserver = null
    }

    private fun resolveAvailableLens(
        provider: ProcessCameraProvider,
        requested: CameraLensOption
    ): CameraLensOption? {
        if (runCatching { provider.hasCamera(requested.toCameraSelector()) }.getOrDefault(false)) {
            return requested
        }
        val fallback = requested.toggled()
        if (runCatching { provider.hasCamera(fallback.toCameraSelector()) }.getOrDefault(false)) {
            return fallback
        }
        return null
    }

    private fun CameraLensOption.toCameraSelector(): CameraSelector = when (this) {
        CameraLensOption.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        CameraLensOption.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
    }

    private fun buildResolutionSelector(option: CaptureResolutionOption): ResolutionSelector {
        val strategy = when (option) {
            CaptureResolutionOption.MAX -> ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
            CaptureResolutionOption.BALANCED,
            CaptureResolutionOption.STANDARD -> ResolutionStrategy(
                Size(option.targetLongSide, option.targetLongSide),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
        }
        return ResolutionSelector.Builder()
            .setResolutionStrategy(strategy)
            .build()
    }
}
