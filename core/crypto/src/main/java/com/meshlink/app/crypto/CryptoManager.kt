package com.meshlink.app.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoManager @Inject constructor() {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    init {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKeyPair()
        }
    }

    private fun generateKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
        )
        val parameterSpecBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setDigests(KeyProperties.DIGEST_SHA256)
            setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            setUserAuthenticationRequired(false)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                parameterSpecBuilder.setIsStrongBoxBacked(true)
                keyPairGenerator.initialize(parameterSpecBuilder.build())
                keyPairGenerator.generateKeyPair()
                return
            } catch (e: Exception) {
                // Fallback to non-StrongBox if not supported
                parameterSpecBuilder.setIsStrongBoxBacked(false)
            }
        }

        keyPairGenerator.initialize(parameterSpecBuilder.build())
        keyPairGenerator.generateKeyPair()
    }

    fun getPublicKeyBase64(): String {
        val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun sign(data: ByteArray): String {
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
        val privateKey: PrivateKey = entry?.privateKey ?: error("Private key not found")

        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(data)
        }
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    fun verify(data: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean {
        try {
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val keyFactory = java.security.KeyFactory.getInstance("EC")
            val publicKey: PublicKey = keyFactory.generatePublic(java.security.spec.X509EncodedKeySpec(publicKeyBytes))

            val signature = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(publicKey)
                update(data)
            }
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            return signature.verify(signatureBytes)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Signature verification failed")
            return false
        }
    }

    companion object {
        private const val KEY_ALIAS = "meshlink_identity_key"
    }
}
