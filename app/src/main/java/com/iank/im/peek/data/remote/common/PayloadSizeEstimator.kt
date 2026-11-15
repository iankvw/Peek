package com.iank.im.peek.data.remote.common

import com.iank.im.peek.data.local.image.InternalImageStorage
import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.ApiProvider
import com.iank.im.peek.domain.model.ImageSendMethod
import com.iank.im.peek.domain.model.PromptPart
import com.iank.im.peek.domain.model.PromptTurn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PayloadSizeEstimator @Inject constructor(
    private val imageStorage: InternalImageStorage
) {
    fun estimateInlineRequestBytes(
        turns: List<PromptTurn>,
        systemPrompt: String
    ): Long {
        var totalBytes = BASE_JSON_ENVELOPE_BYTES + systemPrompt.toByteArray(Charsets.UTF_8).size.toLong()
        for (turn in turns) {
            totalBytes += PER_TURN_JSON_OVERHEAD_BYTES
            for (part in turn.parts) {
                when (part) {
                    is PromptPart.Text -> {
                        totalBytes += PER_TEXT_PART_OVERHEAD_BYTES +
                            (part.text.toByteArray(Charsets.UTF_8).size.toLong() * 11L / 10L)
                    }
                    is PromptPart.ImageRef -> {
                        val file = imageStorage.resolveIfExists(part.payloadRelPath)
                        val rawLen = file?.length() ?: 0L
                        totalBytes += PER_IMAGE_PART_OVERHEAD_BYTES +
                            StreamingJsonBody.base64Length(rawLen)
                    }
                }
            }
        }
        return totalBytes
    }

    fun resolveMaxRequestBytes(
        preset: ApiPreset,
        sendMethod: ImageSendMethod
    ): Long {
        preset.maxRequestBytes?.let { if (it > 0L) return it }
        return when (preset.provider) {
            ApiProvider.GEMINI -> {
                if (sendMethod == ImageSendMethod.FILE_API) {
                    DEFAULT_GEMINI_FILE_API_REQUEST_BYTES
                } else {
                    DEFAULT_GEMINI_INLINE_MAX_BYTES
                }
            }
            ApiProvider.OPENAI -> DEFAULT_OPENAI_INLINE_MAX_BYTES
        }
    }

    companion object {
        // Conservative safe limits under official API ceilings (Gemini 20MB total request, OpenAI ~20-25MB)
        const val DEFAULT_GEMINI_INLINE_MAX_BYTES = 18L * 1024L * 1024L
        const val DEFAULT_GEMINI_FILE_API_REQUEST_BYTES = 18L * 1024L * 1024L
        const val DEFAULT_OPENAI_INLINE_MAX_BYTES = 19L * 1024L * 1024L

        private const val BASE_JSON_ENVELOPE_BYTES = 2048L
        private const val PER_TURN_JSON_OVERHEAD_BYTES = 128L
        private const val PER_TEXT_PART_OVERHEAD_BYTES = 64L
        private const val PER_IMAGE_PART_OVERHEAD_BYTES = 256L
    }
}
