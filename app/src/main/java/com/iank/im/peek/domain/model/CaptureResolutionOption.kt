package com.iank.im.peek.domain.model

enum class CaptureResolutionOption(val targetLongSide: Int) {
    MAX(4096),
    BALANCED(2560),
    STANDARD(1600)
}
