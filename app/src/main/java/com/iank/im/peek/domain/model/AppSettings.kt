package com.iank.im.peek.domain.model

data class AppSettings(
    val selectedPresetId: String? = null,
    val imageSendMethod: ImageSendMethod = ImageSendMethod.FILE_API,
    val defaultResolutionPreset: ImageResolutionPreset = ImageResolutionPreset.STANDARD_2000,
    val contextRetentionMode: ContextRetentionMode = ContextRetentionMode.SlidingWindow(),
    val autoTextReplaceOldImages: Boolean = true,
    val keepRecentImageTurns: Int = 1,
    val backgroundCaptionEnabled: Boolean = false,
    val captionModelId: String = "",
    val defaultCameraLens: CameraLensOption = CameraLensOption.BACK,
    val captureResolution: CaptureResolutionOption = CaptureResolutionOption.BALANCED,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val systemPrompt: String = "",
    val openAiImageDetail: OpenAiImageDetail = OpenAiImageDetail.AUTO,
    val openAiUseDeveloperRole: Boolean = false
)
