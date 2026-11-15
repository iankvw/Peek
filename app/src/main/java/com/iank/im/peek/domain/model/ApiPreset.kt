package com.iank.im.peek.domain.model

data class ApiPreset(
    val id: String,
    val alias: String,
    val provider: ApiProvider,
    val customBaseUrlEnabled: Boolean,
    val customBaseUrl: String?,
    val modelId: String,
    val imagePayloadFormat: ImagePayloadFormat,
    val maxRequestBytes: Long?,
    val createdAt: Long
) {
    val effectiveSendMethod: (ImageSendMethod) -> ImageSendMethod = { preferred ->
        when (provider) {
            ApiProvider.GEMINI -> preferred
            ApiProvider.OPENAI -> ImageSendMethod.BASE64
        }
    }
}
