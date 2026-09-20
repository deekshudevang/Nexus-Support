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
}
