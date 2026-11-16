package com.iank.im.peek.domain.repository

import com.iank.im.peek.domain.model.ApiPreset
import com.iank.im.peek.domain.model.AppLanguage
import com.iank.im.peek.domain.model.AppSettings
import com.iank.im.peek.domain.model.CameraLensOption
import com.iank.im.peek.domain.model.CaptureResolutionOption
import com.iank.im.peek.domain.model.ContextRetentionMode
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.model.ImageSendMethod
import com.iank.im.peek.domain.model.ModelGroup
import com.iank.im.peek.domain.model.OpenAiImageDetail
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    val presets: Flow<List<ApiPreset>>
    val modelGroups: Flow<List<ModelGroup>>
    val selectedPreset: Flow<ApiPreset?>
    val keystoreCorruptedEvent: Flow<Boolean>

    suspend fun getSettings(): AppSettings
    suspend fun getPresets(): List<ApiPreset>
    suspend fun getPreset(presetId: String): ApiPreset?
    suspend fun getModelGroups(): List<ModelGroup>
    suspend fun getModelGroup(groupId: String): ModelGroup?
    suspend fun selectPreset(presetId: String)
    suspend fun upsertPreset(preset: ApiPreset, apiKey: String?)
    suspend fun deletePreset(presetId: String)
    suspend fun upsertModelGroup(group: ModelGroup)
    suspend fun deleteModelGroup(groupId: String)
    suspend fun updateModelGroupLastUsedIndex(groupId: String, lastUsedIndex: Int)

    suspend fun getApiKey(presetId: String): String?
    suspend fun hasApiKey(presetId: String): Boolean
    suspend fun getCredentialFingerprint(apiKey: String): String
    suspend fun acknowledgeKeystoreCorrupted()

    suspend fun updateImageSendMethod(method: ImageSendMethod)
    suspend fun updateDefaultResolutionPreset(preset: ImageResolutionPreset)
    suspend fun updateContextRetentionMode(mode: ContextRetentionMode)
    suspend fun updateAutoTextReplaceOldImages(enabled: Boolean)
    suspend fun updateKeepRecentImageTurns(turns: Int)
    suspend fun updateBackgroundCaptionEnabled(enabled: Boolean)
    suspend fun updateCaptionModelId(modelId: String)
    suspend fun updateDefaultCameraLens(lens: CameraLensOption)
    suspend fun updateCaptureResolution(resolution: CaptureResolutionOption)
    suspend fun updateAppLanguage(language: AppLanguage)
    suspend fun updateSystemPrompt(prompt: String)
    suspend fun updateOpenAiImageDetail(detail: OpenAiImageDetail)
    suspend fun updateOpenAiUseDeveloperRole(enabled: Boolean)
}
