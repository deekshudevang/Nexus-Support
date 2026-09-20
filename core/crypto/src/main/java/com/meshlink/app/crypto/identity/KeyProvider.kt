package com.meshlink.app.crypto.identity

import java.security.KeyPair

interface KeyProvider {
    val keyPair: KeyPair
    val publicKeyBytes: ByteArray
    val deviceId: String
    
    /**
     * Performs ECDH with [peerPublicKeyBytes] using the local private key.
     * Returns the raw shared secret (32 bytes for P-256).
     * Throws [java.security.GeneralSecurityException] on invalid peer key.
     */
    fun computeSharedSecret(peerPublicKeyBytes: ByteArray): ByteArray
    
    /**
     * Signs [data] using ECDSA with SHA-256 and returns a Base64 encoded string.
     */
    fun sign(data: ByteArray): String
    
    /**
     * Verifies an ECDSA signature [signatureBase64] against [data] using [publicKeyBase64].
     */
    fun verify(data: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean
}
