package com.iank.im.peek.ui.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
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
    private var boundCameraKey: String? = null
    private var boundCaptureResolution: CaptureResolutionOption? = null
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

    private data class BackCameraSpec(
        val cameraInfo: CameraInfo,
        val cameraId: String,
        val minFocalLength: Float
    )

    private data class LensBindingPlan(
        val resolvedLens: CameraLensOption,
        val selector: CameraSelector,
        val cameraKey: String,
        val targetZoomRatio: Float
    )

    fun bindUseCases(
        provider: ProcessCameraProvider,
        requestedLens: CameraLensOption,
        captureResolution: CaptureResolutionOption,
        onLensFallback: (CameraLensOption) -> Unit,
        onCameraError: (UiText?) -> Unit
    ) {
        onCameraError(null)

        val plan = resolveLensPlan(provider, requestedLens)
        if (plan == null) {
            unbindOwnUseCases(provider)
            onCameraError(UiText.StringResource(R.string.camera_error_generic, "NO_CAMERA"))
            return
        }
        if (plan.resolvedLens != requestedLens) {
            onLensFallback(plan.resolvedLens)
        }

        // Fast path: if already bound to the same underlying camera & capture resolution, just update zoom ratio
        val existingCamera = boundCamera
        if (existingCamera != null &&
            boundPreview != null &&
            boundImageCapture != null &&
            boundCameraKey == plan.cameraKey &&
            boundCaptureResolution == captureResolution
        ) {
            boundPreview?.surfaceProvider = currentSurfaceProvider
            applyZoomRatio(existingCamera, plan.targetZoomRatio)
            return
        }

        unbindOwnUseCases(provider)

        val preview = Preview.Builder().build().apply {
            surfaceProvider = currentSurfaceProvider
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(buildResolutionSelector(captureResolution))
            .build()

        var activePlan = plan
        var bindResult = runCatching {
            provider.bindToLifecycle(gatedOwner, activePlan.selector, preview, imageCapture)
        }

        // Fallback to default logical back camera + zoom ratio if binding a specific physical camera failed
        if (bindResult.isFailure && !activePlan.resolvedLens.isFront && activePlan.cameraKey != "BACK_DEFAULT") {
            val fallbackZoom = activePlan.resolvedLens.nominalZoomRatio
            activePlan = LensBindingPlan(
                resolvedLens = activePlan.resolvedLens,
                selector = CameraSelector.DEFAULT_BACK_CAMERA,
                cameraKey = "BACK_DEFAULT",
                targetZoomRatio = fallbackZoom
            )
            bindResult = runCatching {
                provider.bindToLifecycle(gatedOwner, activePlan.selector, preview, imageCapture)
            }
        }

        bindResult.onSuccess { camera ->
            boundPreview = preview
            boundImageCapture = imageCapture
            boundCamera = camera
            boundCameraKey = activePlan.cameraKey
            boundCaptureResolution = captureResolution
            applyZoomRatio(camera, activePlan.targetZoomRatio)
            observeCameraState(camera, onCameraError)
        }.onFailure { throwable ->
            boundPreview = null
            boundImageCapture = null
            boundCamera = null
            boundCameraKey = null
            boundCaptureResolution = null
            onCameraError(
                UiText.StringResource(
                    R.string.camera_error_generic,
                    throwable.javaClass.simpleName
                )
            )
        }
    }

    private fun applyZoomRatio(camera: Camera, desiredZoomRatio: Float) {
        val zoomState = camera.cameraInfo.zoomState.value
        val minZoom = zoomState?.minZoomRatio ?: 0.5f
        val maxZoom = zoomState?.maxZoomRatio ?: 8.0f
        val clampedZoom = desiredZoomRatio.coerceIn(minZoom, maxZoom)
        runCatching {
            camera.cameraControl.setZoomRatio(clampedZoom)
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

    fun unbindAllOwnUseCases() {
        cameraProvider?.let { unbindOwnUseCases(it) }
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
        boundCameraKey = null
        boundCaptureResolution = null
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

    private fun resolveLensPlan(
        provider: ProcessCameraProvider,
        requested: CameraLensOption
    ): LensBindingPlan? {
        val hasBack = runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
        val hasFront = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)

        if (requested.isFront) {
            if (hasFront) {
                return LensBindingPlan(
                    resolvedLens = CameraLensOption.FRONT,
                    selector = CameraSelector.DEFAULT_FRONT_CAMERA,
                    cameraKey = "FRONT",
                    targetZoomRatio = 1.0f
                )
            }
            if (hasBack) {
                return buildBackLensPlan(provider, CameraLensOption.BACK)
            }
            return null
        } else {
            if (hasBack) {
                return buildBackLensPlan(provider, requested)
            }
            if (hasFront) {
                return LensBindingPlan(
                    resolvedLens = CameraLensOption.FRONT,
                    selector = CameraSelector.DEFAULT_FRONT_CAMERA,
                    cameraKey = "FRONT",
                    targetZoomRatio = 1.0f
                )
            }
            return null
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun inspectBackCameraSpecs(provider: ProcessCameraProvider): List<BackCameraSpec> {
        return runCatching {
            provider.availableCameraInfos.mapNotNull { info ->
                val c2 = Camera2CameraInfo.from(info)
                val facing = c2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                if (facing != CameraCharacteristics.LENS_FACING_BACK) return@mapNotNull null
                val focals = c2.getCameraCharacteristic(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                )
                val minFocal = focals?.minOrNull() ?: return@mapNotNull null
                BackCameraSpec(
                    cameraInfo = info,
                    cameraId = c2.cameraId,
                    minFocalLength = minFocal
                )
            }
        }.getOrDefault(emptyList())
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun buildBackLensPlan(
        provider: ProcessCameraProvider,
        requestedBackLens: CameraLensOption
    ): LensBindingPlan {
        val specs = inspectBackCameraSpecs(provider)
        if (specs.size > 1) {
            val defaultBackInfo = runCatching {
                CameraSelector.DEFAULT_BACK_CAMERA.filter(provider.availableCameraInfos).firstOrNull()
            }.getOrNull()
            val standardSpec = specs.firstOrNull { it.cameraInfo == defaultBackInfo }
                ?: specs.sortedBy { it.minFocalLength }[specs.size / 2]

            val matchedSpec: BackCameraSpec? = when (requestedBackLens) {
                CameraLensOption.BACK_ULTRA_WIDE -> specs
                    .filter { it.minFocalLength < standardSpec.minFocalLength * 0.85f }
                    .minByOrNull { it.minFocalLength }
                CameraLensOption.BACK -> standardSpec
                CameraLensOption.BACK_TELEPHOTO -> specs
                    .filter { it.minFocalLength > standardSpec.minFocalLength * 1.35f }
                    .maxByOrNull { it.minFocalLength }
                CameraLensOption.FRONT -> null
            }

            if (matchedSpec != null) {
                val targetId = matchedSpec.cameraId
                val customSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .addCameraFilter { cameraInfos ->
                        cameraInfos.filter { info ->
                            runCatching { Camera2CameraInfo.from(info).cameraId == targetId }
                                .getOrDefault(false)
                        }
                    }
                    .build()

                if (runCatching { provider.hasCamera(customSelector) }.getOrDefault(false)) {
                    return LensBindingPlan(
                        resolvedLens = requestedBackLens,
                        selector = customSelector,
                        cameraKey = "BACK_ID:$targetId",
                        targetZoomRatio = 1.0f
                    )
                }
            }
        }

        // Single logical back camera (or physical lens combined under logical camera): use setZoomRatio preset
        return LensBindingPlan(
            resolvedLens = requestedBackLens,
            selector = CameraSelector.DEFAULT_BACK_CAMERA,
            cameraKey = "BACK_DEFAULT",
            targetZoomRatio = requestedBackLens.nominalZoomRatio
        )
    }

    private fun buildResolutionSelector(option: CaptureResolutionOption): ResolutionSelector {
        val strategy = when (option) {
            CaptureResolutionOption.MAX -> ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
            CaptureResolutionOption.HIGH,
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
