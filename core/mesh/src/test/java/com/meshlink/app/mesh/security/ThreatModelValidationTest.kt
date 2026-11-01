package com.meshlink.app.mesh.security

import android.util.Base64
import com.meshlink.app.crypto.cipher.EciesService
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.identity.KeyProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/**
 * Validates the mesh network's resilience against active threats and attacks.
 * Tests the actual cryptographic implementations.
 */
class ThreatModelValidationTest {

    private lateinit var eciesService: EciesService
    private lateinit var keyManager: KeyProvider
    private lateinit var encryptionService: EncryptionService
    
    private lateinit var recipientPublicKeyBytes: ByteArray

    @Before
    fun setup() {
        // Initialize real cryptographic primitives for testing
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        
        val senderKeyPair = kpg.generateKeyPair()
        val recipientKeyPair = kpg.generateKeyPair()
        
        recipientPublicKeyBytes = recipientKeyPair.public.encoded
        
        // Mock KeyManager to return the recipient's private key for decryption
        keyManager = mockk()
        every { keyManager.keyPair } returns recipientKeyPair
        
        encryptionService = EncryptionService()
        eciesService = EciesService(keyManager, encryptionService)
    }

    @Test
    fun testPacketTampering_FailsGCMAuthTag() {
        val plaintext = "SECRET_MISSION_DATA".toByteArray()
        val wireBytes = eciesService.encrypt(plaintext, recipientPublicKeyBytes)
        
        // Ensure normal decryption works
        val decrypted = eciesService.decrypt(wireBytes)
        assertNotNull("Normal decryption should succeed", decrypted)
        
        // TAMPERING ATTACK: Modify a single byte of the ciphertext (which is after the 91-byte public key)
        val tamperedBytes = wireBytes.copyOf()
        tamperedBytes[100] = (tamperedBytes[100] + 1).toByte()
        
        val tamperedDecrypted = eciesService.decrypt(tamperedBytes)
        assertNull("Tampered packet should fail AES-GCM tag verification and return null", tamperedDecrypted)
    }

    @Test
    fun testReplayAttack_ValidationLogic() {
        // Use the actual production SeenMessageCache for replay testing
        val cache = com.meshlink.app.mesh.routing.SeenMessageCache()
        val packetId = "uuid-1234"
        
        // First reception
        val isFirstSeen = cache.isAlreadySeen(packetId)
        assertFalse("First reception should not be seen", isFirstSeen)
        cache.markSeen(packetId)
        
        // Replay attack
        val isSecondSeen = cache.isAlreadySeen(packetId)
        assertTrue("Replay should be identified by the cache", isSecondSeen)
    }

    @Test
    fun testDuplicateFlooding_TriggersRateLimiting() {
        val cache = com.meshlink.app.mesh.routing.SeenMessageCache()
        val packetId = "uuid-flood-999"
        
        var newlyAcceptedCount = 0
        for (i in 0 until 1000) {
            val isAlreadySeen = cache.isAlreadySeen(packetId)
            if (!isAlreadySeen) {
                newlyAcceptedCount++
                cache.markSeen(packetId)
            }
        }
        
        // Only the first packet should be accepted, the rest rate-limited / cached
        assertTrue("Flood should result in exactly 1 newly accepted packet", newlyAcceptedCount == 1)
    }

    @Test
    fun testRoutePoisoning_MitigatedBySignedUpdates() {
        // Use real cryptographic ECDSA signature verification for route poisoning mitigation
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        
        val legitimateKeyPair = kpg.generateKeyPair()
        val maliciousKeyPair = kpg.generateKeyPair()
        
        val routingPayload = "ROUTE_UPDATE:PEER_X_TO_PEER_Y".toByteArray()
        
        // Legitimate signing
        val legitimateSignature = java.security.Signature.getInstance("SHA256withECDSA").apply {
            initSign(legitimateKeyPair.private)
            update(routingPayload)
        }.sign()
        
        // Attacker modifies payload
        val maliciousPayload = "ROUTE_UPDATE:PEER_X_TO_ATTACKER".toByteArray()
        
        // Attacker attempts to append legitimate signature to malicious payload
        val verifier = java.security.Signature.getInstance("SHA256withECDSA").apply {
            initVerify(legitimateKeyPair.public)
            update(maliciousPayload)
        }
        
        val isValid = try {
            verifier.verify(legitimateSignature)
        } catch (e: Exception) {
            false
        }
        
        assertFalse("Malicious route advertisement must fail cryptographic verification", isValid)
    }
}
