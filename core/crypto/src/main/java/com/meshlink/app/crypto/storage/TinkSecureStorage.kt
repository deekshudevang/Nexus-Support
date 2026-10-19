package com.meshlink.app.crypto.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import timber.log.Timber

/**
 * Secure storage backed by Android KeyStore and Google Tink AEAD (AES256-GCM).
 *
 * Replaces deprecated androidx.security:security-crypto with modern Keystore-backed
 * authenticated encryption with associated data (AEAD).
 */
class TinkSecureStorage(
    private val context: Context,
    prefFileName: String = "meshlink_secure_storage",
    masterKeyAlias: String = "meshlink_tink_master_key"
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(prefFileName, Context.MODE_PRIVATE)
    private val aead: Aead

    init {
        AeadConfig.register()
        val masterKeyUri = "android-keystore://$masterKeyAlias"
        val keysetPrefName = "${prefFileName}_keyset"
        
        val keysetHandle = try {
            AndroidKeysetManager.Builder()
                .withSharedPref(context, keysetPrefName, prefFileName)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(masterKeyUri)
                .build()
                .keysetHandle
        } catch (e: Exception) {
            Timber.w(e, "TinkSecureStorage: KeyStore KMS unavailable ($masterKeyUri), falling back to software keyset")
            AndroidKeysetManager.Builder()
                .withSharedPref(context, keysetPrefName, prefFileName)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .doNotUseKeystore()
                .build()
                .keysetHandle
        }
        aead = keysetHandle.getPrimitive(Aead::class.java)
    }

    /**
     * Retrieves and decrypts a string value for [key].
     * Associated data is bound to the key name to protect against ciphertext substitution.
     */
    fun getString(key: String, defaultValue: String? = null): String? {
        val encryptedBase64 = prefs.getString(key, null) ?: return defaultValue
        return try {
            val ciphertext = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val decryptedBytes = aead.decrypt(ciphertext, key.toByteArray(Charsets.UTF_8))
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "TinkSecureStorage: Failed to decrypt key '$key'")
            defaultValue
        }
    }

    /**
     * Encrypts and persists a string value for [key].
     * Associated data is bound to the key name to protect against ciphertext substitution.
     */
    fun putString(key: String, value: String) {
        val plaintextBytes = value.toByteArray(Charsets.UTF_8)
        val ciphertext = aead.encrypt(plaintextBytes, key.toByteArray(Charsets.UTF_8))
        val encryptedBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(key, encryptedBase64).apply()
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
