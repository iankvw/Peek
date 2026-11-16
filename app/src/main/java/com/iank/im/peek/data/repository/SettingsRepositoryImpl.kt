package com.iank.im.peek.data.repository

import com.iank.im.peek.data.local.datastore.SecureKeyStorage
import com.iank.im.peek.data.local.datastore.SettingsDataStore
import com.iank.im.peek.data.local.db.dao.ApiPresetDao
import com.iank.im.peek.data.local.db.entity.ApiPresetEntity
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
import com.iank.im.peek.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val secureKeyStorage: SecureKeyStorage,
    private val apiPresetDao: ApiPresetDao
) : SettingsRepository {

    override val settings: Flow<AppSettings> = settingsDataStore.settingsFlow

    override val presets: Flow<List<ApiPreset>> = apiPresetDao.observeAll()
        .map { list -> list.map { it.toDomain() } }

    override val modelGroups: Flow<List<ModelGroup>> = settingsDataStore.modelGroupsFlow

    override val selectedPreset: Flow<ApiPreset?> = combine(
        presets,
        modelGroups,
        settings
    ) { allPresets, allGroups, current ->
        val selectedId = current.selectedPresetId
        val directPreset = allPresets.firstOrNull { it.id == selectedId }
        if (directPreset != null) {
            directPreset
        } else {
            val matchedGroup = allGroups.firstOrNull { it.id == selectedId }
            val firstGroupPreset = matchedGroup?.memberPresetIds?.firstNotNullOfOrNull { memberId ->
                allPresets.firstOrNull { it.id == memberId }
            }
            firstGroupPreset ?: allPresets.firstOrNull()
        }
    }

    override val keystoreCorruptedEvent: Flow<Boolean> = secureKeyStorage.keystoreCorruptedEvent

    override suspend fun getSettings(): AppSettings = settingsDataStore.getSettings()

    override suspend fun getPresets(): List<ApiPreset> = apiPresetDao.getAll().map { it.toDomain() }

    override suspend fun getPreset(presetId: String): ApiPreset? =
        apiPresetDao.getById(presetId)?.toDomain()

    override suspend fun getModelGroups(): List<ModelGroup> = settingsDataStore.getModelGroups()

    override suspend fun getModelGroup(groupId: String): ModelGroup? =
        settingsDataStore.getModelGroups().firstOrNull { it.id == groupId }

    override suspend fun selectPreset(presetId: String) {
        settingsDataStore.setSelectedPresetId(presetId)
    }

    override suspend fun upsertPreset(preset: ApiPreset, apiKey: String?) {
        apiPresetDao.upsert(preset.toEntity())
        if (apiKey != null) {
            secureKeyStorage.saveApiKey(preset.id, apiKey)
        }
        val currentSettings = settingsDataStore.getSettings()
        if (currentSettings.selectedPresetId == null) {
            settingsDataStore.setSelectedPresetId(preset.id)
        }
    }

    override suspend fun deletePreset(presetId: String) {
        apiPresetDao.deleteById(presetId)
        secureKeyStorage.deleteApiKey(presetId)

        // Remove deleted preset from any model groups; drop groups that fall below 2 members
        val currentGroups = settingsDataStore.getModelGroups()
        val updatedGroups = currentGroups.mapNotNull { group ->
            val filtered = group.memberPresetIds.filterNot { it == presetId }
            if (filtered.size >= 2) {
                group.copy(
                    memberPresetIds = filtered,
                    lastUsedIndex = group.lastUsedIndex.coerceIn(-1, filtered.size - 1)
                )
            } else {
                null
            }
        }
        if (updatedGroups != currentGroups) {
            settingsDataStore.setModelGroups(updatedGroups)
        }

        val currentSettings = settingsDataStore.getSettings()
        val selectedId = currentSettings.selectedPresetId
        val remainingPresets = apiPresetDao.getAll()
        val selectedStillExists = remainingPresets.any { it.id == selectedId } ||
            updatedGroups.any { it.id == selectedId }
        if (!selectedStillExists) {
            settingsDataStore.setSelectedPresetId(
                remainingPresets.firstOrNull()?.id ?: updatedGroups.firstOrNull()?.id
            )
        }
    }

    override suspend fun upsertModelGroup(group: ModelGroup) {
        val current = settingsDataStore.getModelGroups().toMutableList()
        val idx = current.indexOfFirst { it.id == group.id }
        if (idx >= 0) {
            current[idx] = group
        } else {
            current.add(group)
        }
        settingsDataStore.setModelGroups(current)
    }

    override suspend fun deleteModelGroup(groupId: String) {
        val updated = settingsDataStore.getModelGroups().filterNot { it.id == groupId }
        settingsDataStore.setModelGroups(updated)
        val currentSettings = settingsDataStore.getSettings()
        if (currentSettings.selectedPresetId == groupId) {
            val remainingPresets = apiPresetDao.getAll()
            settingsDataStore.setSelectedPresetId(
                remainingPresets.firstOrNull()?.id ?: updated.firstOrNull()?.id
            )
        }
    }

    override suspend fun updateModelGroupLastUsedIndex(groupId: String, lastUsedIndex: Int) {
        val current = settingsDataStore.getModelGroups().toMutableList()
        val idx = current.indexOfFirst { it.id == groupId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(lastUsedIndex = lastUsedIndex)
            settingsDataStore.setModelGroups(current)
        }
    }

    override suspend fun getApiKey(presetId: String): String? =
        secureKeyStorage.getApiKey(presetId)

    override suspend fun hasApiKey(presetId: String): Boolean =
        secureKeyStorage.hasApiKey(presetId)

    override suspend fun getCredentialFingerprint(apiKey: String): String =
        secureKeyStorage.computeCredentialFingerprint(apiKey)

    override suspend fun acknowledgeKeystoreCorrupted() {
        secureKeyStorage.acknowledgeKeystoreCorrupted()
    }

    override suspend fun updateImageSendMethod(method: ImageSendMethod) {
        settingsDataStore.setImageSendMethod(method)
    }

    override suspend fun updateDefaultResolutionPreset(preset: ImageResolutionPreset) {
        settingsDataStore.setDefaultResolutionPreset(preset)
    }

    override suspend fun updateContextRetentionMode(mode: ContextRetentionMode) {
        settingsDataStore.setContextRetentionMode(mode)
    }

    override suspend fun updateAutoTextReplaceOldImages(enabled: Boolean) {
        settingsDataStore.setAutoTextReplaceOldImages(enabled)
    }

    override suspend fun updateKeepRecentImageTurns(turns: Int) {
        settingsDataStore.setKeepRecentImageTurns(turns)
    }

    override suspend fun updateBackgroundCaptionEnabled(enabled: Boolean) {
        settingsDataStore.setBackgroundCaptionEnabled(enabled)
    }

    override suspend fun updateCaptionModelId(modelId: String) {
        settingsDataStore.setCaptionModelId(modelId)
    }

    override suspend fun updateDefaultCameraLens(lens: CameraLensOption) {
        settingsDataStore.setDefaultCameraLens(lens)
    }

    override suspend fun updateCaptureResolution(resolution: CaptureResolutionOption) {
        settingsDataStore.setCaptureResolution(resolution)
    }

    override suspend fun updateAppLanguage(language: AppLanguage) {
        settingsDataStore.setAppLanguage(language)
    }

    override suspend fun updateSystemPrompt(prompt: String) {
        settingsDataStore.setSystemPrompt(prompt)
    }

    override suspend fun updateOpenAiImageDetail(detail: OpenAiImageDetail) {
        settingsDataStore.setOpenAiImageDetail(detail)
    }

    override suspend fun updateOpenAiUseDeveloperRole(enabled: Boolean) {
        settingsDataStore.setOpenAiUseDeveloperRole(enabled)
    }

    private fun ApiPresetEntity.toDomain(): ApiPreset = ApiPreset(
        id = id,
        alias = alias,
        provider = provider,
        customBaseUrlEnabled = customBaseUrlEnabled,
        customBaseUrl = customBaseUrl,
        modelId = modelId,
        imagePayloadFormat = imagePayloadFormat,
        maxRequestBytes = maxRequestBytes,
        createdAt = createdAt
    )

    private fun ApiPreset.toEntity(): ApiPresetEntity = ApiPresetEntity(
        id = id,
        alias = alias,
        provider = provider,
        customBaseUrlEnabled = customBaseUrlEnabled,
        customBaseUrl = customBaseUrl,
        modelId = modelId,
        imagePayloadFormat = imagePayloadFormat,
        maxRequestBytes = maxRequestBytes,
        createdAt = createdAt
    )
}
