package com.iank.im.peek.domain.repository

import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.AppSettings
import com.iank.im.peek.domain.model.ImageSendMethod
import com.iank.im.peek.domain.model.MessageAttachment
import com.iank.im.peek.domain.model.PreflightNotice
import com.iank.im.peek.domain.model.PromptTurn
import com.iank.im.peek.domain.model.VisionResponseResult

interface VisionAiRepository {
    suspend fun generateVisionResponse(
        preset: ApiPreset,
        apiKey: String,
        systemPrompt: String,
        turns: List<PromptTurn>,
        settings: AppSettings,
        effectiveSendMethod: ImageSendMethod,
        onNotice: suspend (PreflightNotice) -> Unit = {}
    ): VisionResponseResult

    suspend fun testConnection(preset: ApiPreset, apiKey: String): Result<Unit>

    suspend fun generateImageCaption(
        preset: ApiPreset,
        apiKey: String,
        attachment: MessageAttachment,
        modelIdOverride: String?
    ): String?

    suspend fun processPendingRemoteDeletions()
}
