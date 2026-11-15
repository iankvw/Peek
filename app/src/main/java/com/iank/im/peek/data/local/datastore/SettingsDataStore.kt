package com.iank.im.peek.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.iank.im.peek.domain.model.AppLanguage
import com.iank.im.peek.domain.model.AppSettings
import com.iank.im.peek.domain.model.CameraLensOption
import com.iank.im.peek.domain.model.CaptureResolutionOption
import com.iank.im.peek.domain.model.ContextRetentionMode
import com.iank.im.peek.domain.model.ImageResolutionPreset
import com.iank.im.peek.domain.model.ImageSendMethod
import com.iank.im.peek.domain.model.OpenAiImageDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    @param:Named("settings_datastore") private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private object Keys {
        val SELECTED_PRESET_ID = stringPreferencesKey("selected_preset_id")
        val IMAGE_SEND_METHOD = stringPreferencesKey("image_send_method")
        val DEFAULT_RESOLUTION_PRESET = stringPreferencesKey("default_resolution_preset")
        val CONTEXT_RETENTION_MODE = stringPreferencesKey("context_retention_mode")
        val AUTO_TEXT_REPLACE_OLD_IMAGES = booleanPreferencesKey("auto_text_replace_old_images")
        val KEEP_RECENT_IMAGE_TURNS = intPreferencesKey("keep_recent_image_turns")
        val BACKGROUND_CAPTION_ENABLED = booleanPreferencesKey("background_caption_enabled")
        val CAPTION_MODEL_ID = stringPreferencesKey("caption_model_id")
        val DEFAULT_CAMERA_LENS = stringPreferencesKey("default_camera_lens")
        val CAPTURE_RESOLUTION = stringPreferencesKey("capture_resolution")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val OPENAI_IMAGE_DETAIL = stringPreferencesKey("openai_image_detail")
        val OPENAI_USE_DEVELOPER_ROLE = booleanPreferencesKey("openai_use_developer_role")
        val LAST_ORPHAN_SWEEP_AT = longPreferencesKey("last_orphan_sweep_at")
        val APP_SALT = stringPreferencesKey("app_salt")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> mapPreferencesToSettings(prefs) }

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    suspend fun setSelectedPresetId(presetId: String?) {
        dataStore.edit { prefs ->
            if (presetId == null) {
                prefs.remove(Keys.SELECTED_PRESET_ID)
            } else {
                prefs[Keys.SELECTED_PRESET_ID] = presetId
            }
        }
    }

    suspend fun setImageSendMethod(method: ImageSendMethod) {
        dataStore.edit { it[Keys.IMAGE_SEND_METHOD] = method.name }
    }

    suspend fun setDefaultResolutionPreset(preset: ImageResolutionPreset) {
        val normalized = if (preset == ImageResolutionPreset.DEFAULT) {
            ImageResolutionPreset.STANDARD_2000
        } else {
            preset
        }
        dataStore.edit { it[Keys.DEFAULT_RESOLUTION_PRESET] = normalized.name }
    }

    suspend fun setContextRetentionMode(mode: ContextRetentionMode) {
        val encoded = json.encodeToString(ContextRetentionMode.serializer(), mode)
        dataStore.edit { it[Keys.CONTEXT_RETENTION_MODE] = encoded }
    }

    suspend fun setAutoTextReplaceOldImages(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_TEXT_REPLACE_OLD_IMAGES] = enabled }
    }

    suspend fun setKeepRecentImageTurns(turns: Int) {
        dataStore.edit { it[Keys.KEEP_RECENT_IMAGE_TURNS] = turns.coerceIn(1, 20) }
    }

    suspend fun setBackgroundCaptionEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKGROUND_CAPTION_ENABLED] = enabled }
    }

    suspend fun setCaptionModelId(modelId: String) {
        dataStore.edit { it[Keys.CAPTION_MODEL_ID] = modelId.trim() }
    }

    suspend fun setDefaultCameraLens(lens: CameraLensOption) {
        dataStore.edit { it[Keys.DEFAULT_CAMERA_LENS] = lens.name }
    }

    suspend fun setCaptureResolution(resolution: CaptureResolutionOption) {
        dataStore.edit { it[Keys.CAPTURE_RESOLUTION] = resolution.name }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.APP_LANGUAGE] = language.name }
    }

    suspend fun setSystemPrompt(prompt: String) {
        dataStore.edit { it[Keys.SYSTEM_PROMPT] = prompt }
    }

    suspend fun setOpenAiImageDetail(detail: OpenAiImageDetail) {
        dataStore.edit { it[Keys.OPENAI_IMAGE_DETAIL] = detail.name }
    }

    suspend fun setOpenAiUseDeveloperRole(enabled: Boolean) {
        dataStore.edit { it[Keys.OPENAI_USE_DEVELOPER_ROLE] = enabled }
    }

    suspend fun getLastOrphanSweepAt(): Long {
        return dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()[Keys.LAST_ORPHAN_SWEEP_AT] ?: 0L
    }

    suspend fun setLastOrphanSweepAt(timestampMillis: Long) {
        dataStore.edit { it[Keys.LAST_ORPHAN_SWEEP_AT] = timestampMillis }
    }

    suspend fun getOrCreateAppSalt(): String {
        val existing = dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()[Keys.APP_SALT]
        if (!existing.isNullOrBlank()) return existing

        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val generated = bytes.joinToString(separator = "") { "%02x".format(it) }
        dataStore.edit { prefs ->
            if (prefs[Keys.APP_SALT].isNullOrBlank()) {
                prefs[Keys.APP_SALT] = generated
            }
        }
        return dataStore.data.first()[Keys.APP_SALT] ?: generated
    }

    private fun mapPreferencesToSettings(prefs: Preferences): AppSettings {
        val retentionMode = prefs[Keys.CONTEXT_RETENTION_MODE]?.let { raw ->
            runCatching { json.decodeFromString(ContextRetentionMode.serializer(), raw) }.getOrNull()
        } ?: ContextRetentionMode.SlidingWindow()

        val defaultResolution = prefs[Keys.DEFAULT_RESOLUTION_PRESET]?.let { raw ->
            ImageResolutionPreset.entries.firstOrNull { it.name == raw && it != ImageResolutionPreset.DEFAULT }
        } ?: ImageResolutionPreset.STANDARD_2000

        return AppSettings(
            selectedPresetId = prefs[Keys.SELECTED_PRESET_ID],
            imageSendMethod = prefs[Keys.IMAGE_SEND_METHOD]?.let { raw ->
                ImageSendMethod.entries.firstOrNull { it.name == raw }
            } ?: ImageSendMethod.FILE_API,
            defaultResolutionPreset = defaultResolution,
            contextRetentionMode = retentionMode,
            autoTextReplaceOldImages = prefs[Keys.AUTO_TEXT_REPLACE_OLD_IMAGES] ?: true,
            keepRecentImageTurns = (prefs[Keys.KEEP_RECENT_IMAGE_TURNS] ?: 1).coerceIn(1, 20),
            backgroundCaptionEnabled = prefs[Keys.BACKGROUND_CAPTION_ENABLED] ?: false,
            captionModelId = prefs[Keys.CAPTION_MODEL_ID] ?: "",
            defaultCameraLens = prefs[Keys.DEFAULT_CAMERA_LENS]?.let { raw ->
                CameraLensOption.entries.firstOrNull { it.name == raw }
            } ?: CameraLensOption.BACK,
            captureResolution = prefs[Keys.CAPTURE_RESOLUTION]?.let { raw ->
                CaptureResolutionOption.entries.firstOrNull { it.name == raw }
            } ?: CaptureResolutionOption.BALANCED,
            appLanguage = AppLanguage.fromStoredName(prefs[Keys.APP_LANGUAGE]),
            systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: "",
            openAiImageDetail = prefs[Keys.OPENAI_IMAGE_DETAIL]?.let { raw ->
                OpenAiImageDetail.entries.firstOrNull { it.name == raw }
            } ?: OpenAiImageDetail.AUTO,
            openAiUseDeveloperRole = prefs[Keys.OPENAI_USE_DEVELOPER_ROLE] ?: false
        )
    }
}
