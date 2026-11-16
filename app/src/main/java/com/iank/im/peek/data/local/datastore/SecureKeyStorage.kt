package com.iank.im.peek.data.local.datastore

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SecureKeyStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Named("secure_keys_datastore") private val dataStore: DataStore<Preferences>,
    private val settingsDataStore: SettingsDataStore
) {
    private val mutex = Mutex()

    @Volatile
    private var cachedAead: Aead? = null

    private val _keystoreCorruptedEvent = MutableStateFlow(false)
    val keystoreCorruptedEvent: Flow<Boolean> = _keystoreCorruptedEvent.asStateFlow()

    fun acknowledgeKeystoreCorrupted() {
        _keystoreCorruptedEvent.value = false
    }

    suspend fun saveApiKey(presetId: String, apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            deleteApiKey(presetId)
            return
        }
        mutex.withLock {
            val aead = getOrCreateAeadLocked()
            val encrypted = try {
                aead.encrypt(
                    trimmed.toByteArray(Charsets.UTF_8),
                    presetId.toByteArray(Charsets.UTF_8)
                )
            } catch (_: GeneralSecurityException) {
                val recovered = resetKeystoreAndStorageLocked()
                recovered.encrypt(
                    trimmed.toByteArray(Charsets.UTF_8),
                    presetId.toByteArray(Charsets.UTF_8)
                )
            } catch (_: ProviderException) {
                val recovered = resetKeystoreAndStorageLocked()
                recovered.encrypt(
                    trimmed.toByteArray(Charsets.UTF_8),
                    presetId.toByteArray(Charsets.UTF_8)
                )
            }
            val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            dataStore.edit { prefs ->
                prefs[keyForPreset(presetId)] = encoded
            }
        }
    }

    suspend fun getApiKey(presetId: String): String? = mutex.withLock {
        val prefs = dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        val encoded = prefs[keyForPreset(presetId)] ?: return@withLock null
        val aead = try {
            getOrCreateAeadLocked()
        } catch (_: GeneralSecurityException) {
            return@withLock null
        } catch (_: ProviderException) {
            return@withLock null
        }
        return@withLock try {
            val cipherBytes = Base64.decode(encoded, Base64.NO_WRAP)
            val plainBytes = aead.decrypt(cipherBytes, presetId.toByteArray(Charsets.UTF_8))
            String(plainBytes, Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            runCatching { resetKeystoreAndStorageLocked() }
            null
        } catch (_: ProviderException) {
            runCatching { resetKeystoreAndStorageLocked() }
            null
        } catch (_: IllegalArgumentException) {
            dataStore.edit { it.remove(keyForPreset(presetId)) }
            null
        }
    }

    suspend fun hasApiKey(presetId: String): Boolean {
        return !getApiKey(presetId).isNullOrBlank()
    }

    suspend fun deleteApiKey(presetId: String) {
        dataStore.edit { prefs ->
            prefs.remove(keyForPreset(presetId))
        }
    }

    suspend fun computeCredentialFingerprint(apiKey: String): String {
        val salt = settingsDataStore.getOrCreateAppSalt()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }.take(16)
    }

    private suspend fun getOrCreateAeadLocked(): Aead {
        cachedAead?.let { return it }
        return try {
            buildAead().also { cachedAead = it }
        } catch (_: GeneralSecurityException) {
            resetKeystoreAndStorageLocked()
        } catch (_: ProviderException) {
            resetKeystoreAndStorageLocked()
        } catch (_: IOException) {
            resetKeystoreAndStorageLocked()
        }
    }

    private suspend fun resetKeystoreAndStorageLocked(): Aead {
        cachedAead = null
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
        }
        context.getSharedPreferences(TINK_PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        dataStore.edit { it.clear() }
        _keystoreCorruptedEvent.value = true
        return buildAead().also { cachedAead = it }
    }

    private fun buildAead(): Aead {
        AeadConfig.register()
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, TINK_KEYSET_NAME, TINK_PREF_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private fun keyForPreset(presetId: String) = stringPreferencesKey("key_$presetId")

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "peek_master_key"
        private const val TINK_KEYSET_NAME = "tink_keyset"
        private const val TINK_PREF_FILE = "tink_keyset_prefs"
        private const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
    }
}
