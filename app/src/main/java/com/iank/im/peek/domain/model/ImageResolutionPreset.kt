package com.iank.im.peek.domain.model

enum class ImageResolutionPreset(val maxSidePx: Int?) {
    DEFAULT(null),
    ORIGINAL(MAX_WEBP_DIMENSION),
    HIGH_4000(4000),
    STANDARD_2000(2000),
    SAVER_1000(1000);

    companion object {
        const val MAX_WEBP_DIMENSION = 16383

        fun resolveEffectivePreset(
            attachmentPreset: ImageResolutionPreset,
            conversationOverride: ImageResolutionPreset?,
            globalDefault: ImageResolutionPreset
        ): ImageResolutionPreset {
            return when {
                attachmentPreset != DEFAULT -> attachmentPreset
                conversationOverride != null && conversationOverride != DEFAULT -> conversationOverride
                globalDefault != DEFAULT -> globalDefault
                else -> STANDARD_2000
            }
        }

        fun resolveMaxSidePx(
            attachmentPreset: ImageResolutionPreset,
            conversationOverride: ImageResolutionPreset?,
            globalDefault: ImageResolutionPreset
        ): Int {
            val effective = resolveEffectivePreset(
                attachmentPreset = attachmentPreset,
                conversationOverride = conversationOverride,
                globalDefault = globalDefault
            )
            return effective.maxSidePx ?: 2000
        }

        fun lowerStep(currentMaxSide: Int): ImageResolutionPreset? = when {
            currentMaxSide > 4000 -> HIGH_4000
            currentMaxSide > 2000 -> STANDARD_2000
            currentMaxSide > 1000 -> SAVER_1000
            else -> null
        }
    }
}
