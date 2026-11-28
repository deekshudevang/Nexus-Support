package com.meshlink.app.data.security

import android.content.Context
import android.util.Base64
import timber.log.Timber

interface GatewaySecretStore {
    fun getGatewayKey(): String
    fun setGatewayKey(key: String)
}

/**
 * Encrypts the runtime-provisioned X-Gateway-Key at rest using Tink AEAD (AES-256-GCM)
 * backed by Android KeyStore when available.
 */
class TinkGatewaySecretStore(
    private val context: Context
) : GatewaySecretStore {

    override fun getGatewayKey(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cipherB64 = prefs.getString(KEY_CIPHERTEXT, null) ?: return ""
        return try {
            val aead = SecureDbPassphraseProvider.getAead(context)
            val plainBytes = aead.decrypt(Base64.decode(cipherB64, Base64.NO_WRAP), AAD)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt stored gateway API key")
            ""
        }
    }

    override fun setGatewayKey(key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_CIPHERTEXT).apply()
            return
        }
        val aead = SecureDbPassphraseProvider.getAead(context)
        val cipherBytes = aead.encrypt(trimmed.toByteArray(Charsets.UTF_8), AAD)
        val cipherB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        prefs.edit().putString(KEY_CIPHERTEXT, cipherB64).apply()
    }

    companion object {
        private const val PREFS_NAME = "meshlink_gateway_secure_prefs"
        private const val KEY_CIPHERTEXT = "gateway_api_key_enc"
        private val AAD = "meshlink_gateway_key_v1".toByteArray(Charsets.UTF_8)
    }
}
