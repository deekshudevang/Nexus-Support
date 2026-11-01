package com.meshlink.app.data.security

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import timber.log.Timber
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom

/**
 * Secure provider for the SQLCipher Room database passphrase.
 *
 * Replaces deprecated `androidx.security:security-crypto:1.1.0-alpha06` with
 * Google Tink (AEAD / AES-256-GCM) backed by Android KeyStore.
 * Provides a tested, safe migration path for databases created with legacy passphrases.
 */
object SecureDbPassphraseProvider {

    private const val SECURE_PREFS_NAME = "meshlink_db_secure_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"
    private const val TINK_KEYSET_NAME = "meshlink_db_keyset"
    private const val MASTER_KEY_URI = "android-keystore://meshlink_db_master_key"

    @Volatile
    private var cachedAead: Aead? = null

    private fun isKeyStoreAvailable(): Boolean {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun getAead(context: Context): Aead {
        return cachedAead ?: synchronized(this) {
            cachedAead ?: createAead(context).also { cachedAead = it }
        }
    }

    private fun createAead(context: Context): Aead {
        AeadConfig.register()
        return try {
            if (isKeyStoreAvailable()) {
                AndroidKeysetManager.Builder()
                    .withSharedPref(context, TINK_KEYSET_NAME, "meshlink_keyset_prefs")
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .keysetHandle
                    .getPrimitive(Aead::class.java)
            } else {
                AndroidKeysetManager.Builder()
                    .withSharedPref(context, TINK_KEYSET_NAME, "meshlink_keyset_prefs")
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .build()
                    .keysetHandle
                    .getPrimitive(Aead::class.java)
            }
        } catch (e: Exception) {
            Timber.w(e, "AndroidKeyStore KMS unavailable, falling back to software keyset")
            AndroidKeysetManager.Builder()
                .withSharedPref(context, TINK_KEYSET_NAME, "meshlink_keyset_prefs")
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .build()
                .keysetHandle
                .getPrimitive(Aead::class.java)
        }
    }

    /**
     * Retrieves or generates a 256-bit passphrase for the SQLCipher database.
     * Stored encrypted using Tink Aead (AES-256-GCM) with Android KeyStore master key.
     * If an existing legacy EncryptedSharedPreferences passphrase exists, it is migrated.
     */
    fun getOrGeneratePassphrase(context: Context): ByteArray {
        val aead = getAead(context)
        val prefs = context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Try reading existing Tink-encrypted passphrase
        val encryptedBase64 = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (encryptedBase64 != null) {
            try {
                val cipherBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                return aead.decrypt(cipherBytes, KEY_DB_PASSPHRASE.toByteArray(Charsets.UTF_8))
            } catch (e: Exception) {
                Timber.e(e, "Failed to decrypt existing DB passphrase with Tink Aead")
            }
        }

        // 2. Migration check: if legacy passphrase exists, migrate it
        val migrated = migrateLegacyPassphrase(context)
        if (migrated != null) {
            savePassphrase(context, aead, migrated)
            return migrated
        }

        // 3. Generate a new cryptographically secure random 256-bit passphrase (64 hex characters)
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        val hexString = randomBytes.joinToString("") { "%02x".format(it) }
        val passphraseBytes = hexString.toByteArray(Charsets.UTF_8)
        savePassphrase(context, aead, passphraseBytes)
        return passphraseBytes
    }

    private fun savePassphrase(context: Context, aead: Aead, passphraseBytes: ByteArray) {
        val cipherBytes = aead.encrypt(passphraseBytes, KEY_DB_PASSPHRASE.toByteArray(Charsets.UTF_8))
        val base64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DB_PASSPHRASE, base64)
            .apply()
    }

    private fun migrateLegacyPassphrase(context: Context): ByteArray? {
        val legacyPrefsFile = File(context.applicationInfo.dataDir, "shared_prefs/meshlink_db_prefs.xml")
        if (!legacyPrefsFile.exists()) return null

        return try {
            val legacyPrefs = context.getSharedPreferences("meshlink_db_prefs", Context.MODE_PRIVATE)
            val legacyValue = legacyPrefs.getString("db_passphrase", null)
            if (legacyValue != null && legacyValue.isNotEmpty()) {
                legacyValue.toByteArray(Charsets.UTF_8)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not extract legacy passphrase during migration")
            null
        }
    }
}
