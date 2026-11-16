package com.iank.im.peek.domain.model

enum class CaptureResolutionOption(val targetLongSide: Int) {
    MAX(4096),
    HIGH(4000),
    BALANCED(2000),
    STANDARD(1000)
}
