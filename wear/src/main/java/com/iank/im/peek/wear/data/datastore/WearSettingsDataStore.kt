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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearSettingsDataStore @Inject constructor(
    @param:ApplicationContext context: Context,
    @param:ApplicationScope applicationScope: CoroutineScope
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = applicationScope,
        produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) }
    )

    val imageQuality: Flow<WearImageQuality> = dataStore.data.map { prefs ->
        WearImageQuality.fromStored(prefs[KEY_IMAGE_QUALITY])
    }

    val inputMode: Flow<WearInputMode> = dataStore.data.map { prefs ->
        WearInputMode.fromStored(prefs[KEY_INPUT_MODE])
    }

    suspend fun getImageQuality(): WearImageQuality = imageQuality.first()

    suspend fun getInputMode(): WearInputMode = inputMode.first()

    suspend fun setImageQuality(quality: WearImageQuality) {
        dataStore.edit { prefs ->
            prefs[KEY_IMAGE_QUALITY] = quality.name
        }
    }

    suspend fun setInputMode(mode: WearInputMode) {
        dataStore.edit { prefs ->
            prefs[KEY_INPUT_MODE] = mode.name
        }
    }

    companion object {
        private const val DATASTORE_NAME = "peek_wear_settings"
        private val KEY_IMAGE_QUALITY = stringPreferencesKey("wear_image_quality")
        private val KEY_INPUT_MODE = stringPreferencesKey("wear_input_mode")
    }
}
