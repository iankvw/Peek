package com.iank.im.peek.domain.model

enum class CameraLensOption(
    val isFront: Boolean,
    val nominalZoomRatio: Float,
    val badgeLabel: String
) {
    BACK_ULTRA_WIDE(isFront = false, nominalZoomRatio = 0.6f, badgeLabel = "0.6x"),
    BACK(isFront = false, nominalZoomRatio = 1.0f, badgeLabel = "1x"),
    BACK_TELEPHOTO(isFront = false, nominalZoomRatio = 3.0f, badgeLabel = "3x"),
    FRONT(isFront = true, nominalZoomRatio = 1.0f, badgeLabel = "1x");

    fun toggled(lastRearLens: CameraLensOption = BACK): CameraLensOption =
        if (isFront) {
            if (!lastRearLens.isFront) lastRearLens else BACK
        } else {
            FRONT
        }

    companion object {
        val rearLenses: List<CameraLensOption> = listOf(
            BACK_ULTRA_WIDE,
            BACK,
            BACK_TELEPHOTO
        )
    }
}
