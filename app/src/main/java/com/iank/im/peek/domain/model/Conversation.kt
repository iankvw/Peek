package com.iank.im.peek.domain.model

data class Conversation(
    val id: String,
    val title: String,
    val activeTargetId: String? = null,
    val overrideResolutionPreset: ImageResolutionPreset?,
    val createdAt: Long,
    val updatedAt: Long
)
