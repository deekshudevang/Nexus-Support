package com.meshlink.app.crypto.identity

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductionKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) : KeyProvider {

    companion object {
        private const val PREFS_FILE    = "meshlink_identity_v1"
        private const val KEY_PRIVATE   = "ec_private_key"
        private const val KEY_PUBLIC    = "ec_public_key"
        private const val EC_ALGORITHM  = "EC"
        private const val CURVE         = "secp256r1"
        private const val ECDH_ALGORITHM = "ECDH"
        
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "meshlink_identity_ec_p256"
    }

    private val encryptedPrefs: SharedPreferences by lazy { createEncryptedPrefs() }

    @Volatile
    private var cachedKeyPair: KeyPair? = null

    override val keyPair: KeyPair
        get() = cachedKeyPair ?: loadOrGenerate().also { cachedKeyPair = it }

    override val publicKeyBytes: ByteArray
        get() = keyPair.public.encoded

    override val deviceId: String
        get() = sha256Hex(publicKeyBytes).take(16)

    override fun computeSharedSecret(peerPublicKeyBytes: ByteArray): ByteArray {
        val keyFactory = KeyFactory.getInstance(EC_ALGORITHM)
        val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))

        val keyAgreement = KeyAgreement.getInstance(ECDH_ALGORITHM)
        keyAgreement.init(keyPair.private)
        keyAgreement.doPhase(peerPublicKey, true)
        return keyAgreement.generateSecret()
    }

    override fun sign(data: ByteArray): String {
        val signature = java.security.Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyPair.private)
            update(data)
        }
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    override fun verify(data: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val keyFactory = KeyFactory.getInstance(EC_ALGORITHM)
            val publicKey: java.security.PublicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))

            val signature = java.security.Signature.getInstance("SHA256withECDSA").apply {
                initVerify(publicKey)
                update(data)
            }
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            Timber.e(e, "Signature verification failed")
            false
        }
    }

    private fun loadOrGenerate(): KeyPair {
        // 1. Check legacy software keys first to prevent breaking existing user identities
        val legacyPair = loadLegacySoftwareKeys()
        if (legacyPair != null) {
            Timber.d("KeyManager: Loaded legacy software identity key (deviceId=${sha256Hex(legacyPair.public.encoded).take(16)})")
            return legacyPair
        }

        // 2. Check AndroidKeyStore
        val hardwarePair = loadFromKeyStore()
        if (hardwarePair != null) {
            Timber.d("KeyManager: Loaded hardware identity key (deviceId=${sha256Hex(hardwarePair.public.encoded).take(16)})")
            return hardwarePair
        }

        // 3. Generate in AndroidKeyStore (StrongBox if available)
        return generateInKeyStore()
    }

    private fun loadLegacySoftwareKeys(): KeyPair? {
        val storedPriv = encryptedPrefs.getString(KEY_PRIVATE, null)
        val storedPub  = encryptedPrefs.getString(KEY_PUBLIC,  null)

        if (storedPriv != null && storedPub != null) {
            return try {
                val kf = KeyFactory.getInstance(EC_ALGORITHM)
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(storedPriv, Base64.NO_WRAP)))
                val pub  = kf.generatePublic(X509EncodedKeySpec(Base64.decode(storedPub, Base64.NO_WRAP)))
                KeyPair(pub, priv)
            } catch (e: Exception) {
                Timber.e(e, "KeyManager: failed to load legacy stored keys")
                null
            }
        }
        return null
    }

    private fun loadFromKeyStore(): KeyPair? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!ks.containsAlias(KEYSTORE_ALIAS)) return null
            
            val publicKey = ks.getCertificate(KEYSTORE_ALIAS).publicKey
            val privateKey = ks.getKey(KEYSTORE_ALIAS, null) as PrivateKey
            KeyPair(publicKey, privateKey)
        } catch (e: Exception) {
            Timber.e(e, "KeyManager: failed to load from AndroidKeyStore")
            null
        }
    }

    private fun generateInKeyStore(): KeyPair {
        val hasStrongBox = Build.VERSION.SDK_INT >= 28 &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            
        val builder = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS, KeyProperties.PURPOSE_AGREE_KEY or KeyProperties.PURPOSE_SIGN
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            
        if (hasStrongBox && Build.VERSION.SDK_INT >= 28) {
            builder.setIsStrongBoxBacked(true)
        }
        
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        
        return try {
            generator.initialize(builder.build())
            val kp = generator.generateKeyPair()
            Timber.d("KeyManager: generated new hardware identity key (StrongBox=$hasStrongBox, deviceId=${sha256Hex(kp.public.encoded).take(16)})")
            kp
        } catch (e: java.security.ProviderException) {
            if (hasStrongBox && e.javaClass.simpleName == "StrongBoxUnavailableException") {
                Timber.w(e, "KeyManager: StrongBox unavailable despite feature flag. Falling back to TEE.")
                if (Build.VERSION.SDK_INT >= 28) {
                    builder.setIsStrongBoxBacked(false)
                }
                generator.initialize(builder.build())
                val kp = generator.generateKeyPair()
                Timber.d("KeyManager: generated new hardware identity key (StrongBox=false fallback, deviceId=${sha256Hex(kp.public.encoded).take(16)})")
                kp
            } else {
                throw e
            }
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILE,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().clear().apply()
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILE,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private fun sha256Hex(input: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input)
            .joinToString("") { "%02x".format(it) }
}
