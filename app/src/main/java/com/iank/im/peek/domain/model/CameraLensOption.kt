package com.iank.im.peek.domain.model

enum class CameraLensOption {
    BACK,
    FRONT;

    fun toggled(): CameraLensOption = when (this) {
        BACK -> FRONT
        FRONT -> BACK
    }
}
