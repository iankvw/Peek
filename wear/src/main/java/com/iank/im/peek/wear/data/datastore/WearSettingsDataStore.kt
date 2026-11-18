package com.iank.im.peek.wear.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.iank.im.peek.wear.di.ApplicationScope
import com.iank.im.peek.wear.domain.model.WearImageQuality
import com.iank.im.peek.wear.domain.model.WearInputMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearSettingsDataStore @Inject constructor(
    @param:ApplicationContext context: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = applicationScope,
        produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) }
    )

    private val rawImageQuality: Flow<WearImageQuality> = dataStore.data.map { prefs ->
        WearImageQuality.fromStored(prefs[KEY_IMAGE_QUALITY])
    }

    private val rawDefaultInputMethod: Flow<WearInputMode> = dataStore.data.map { prefs ->
        WearInputMode.fromStored(prefs[KEY_INPUT_MODE])
    }

    val imageQuality: StateFlow<WearImageQuality> = rawImageQuality.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = WearImageQuality.BALANCED_2000
    )

    val defaultInputMethod: StateFlow<WearInputMode> = rawDefaultInputMethod.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = WearInputMode.TEXT_KEYBOARD
    )

    val inputMode: StateFlow<WearInputMode>
        get() = defaultInputMethod

    suspend fun getImageQuality(): WearImageQuality = rawImageQuality.first()

    suspend fun getDefaultInputMethod(): WearInputMode = rawDefaultInputMethod.first()

    suspend fun getInputMode(): WearInputMode = getDefaultInputMethod()

    suspend fun setImageQuality(quality: WearImageQuality) {
        withContext(applicationScope.coroutineContext + NonCancellable) {
            dataStore.edit { prefs ->
                prefs[KEY_IMAGE_QUALITY] = quality.name
            }
        }
    }

    suspend fun setDefaultInputMethod(method: WearInputMode) {
        withContext(applicationScope.coroutineContext + NonCancellable) {
            dataStore.edit { prefs ->
                prefs[KEY_INPUT_MODE] = method.name
            }
        }
    }

    suspend fun setInputMode(mode: WearInputMode) = setDefaultInputMethod(mode)

    suspend fun toggleDefaultInputMethod(): WearInputMode =
        withContext(applicationScope.coroutineContext + NonCancellable) {
            val current = rawDefaultInputMethod.first()
            val next = when (current) {
                WearInputMode.TEXT_KEYBOARD -> WearInputMode.VOICE_FIRST
                WearInputMode.VOICE_FIRST -> WearInputMode.TEXT_KEYBOARD
            }
            dataStore.edit { prefs ->
                prefs[KEY_INPUT_MODE] = next.name
            }
            next
        }

    companion object {
        private const val DATASTORE_NAME = "peek_wear_settings"
        private val KEY_IMAGE_QUALITY = stringPreferencesKey("wear_image_quality")
        private val KEY_INPUT_MODE = stringPreferencesKey("wear_input_mode")
    }
}
