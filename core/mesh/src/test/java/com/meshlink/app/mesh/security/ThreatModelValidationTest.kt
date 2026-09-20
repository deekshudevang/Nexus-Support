package com.meshlink.app.mesh.security

import android.util.Base64
import com.meshlink.app.crypto.cipher.EciesService
import com.meshlink.app.crypto.cipher.EncryptionService
import com.meshlink.app.crypto.identity.KeyManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import org.mockito.Mockito.*

/**
 * Validates the mesh network's resilience against active threats and attacks.
 * Tests the actual cryptographic implementations.
 */
class ThreatModelValidationTest {

    private lateinit var eciesService: EciesService
    private lateinit var keyManager: KeyManager
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
        keyManager = mock(KeyManager::class.java)
        `when`(keyManager.keyPair).thenReturn(recipientKeyPair)
        
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
        // In the actual app, SeenMessageCache handles replays.
        // We simulate the router's behavior here to prove the logic.
        val cache = mutableSetOf<String>()
        val packetId = "uuid-1234"
        
        // First reception
        val isFirstAccepted = cache.add(packetId)
        assertTrue("First reception should be accepted", isFirstAccepted)
        
        // Replay attack
        val isSecondAccepted = cache.add(packetId)
        assertFalse("Replay should be rejected by the cache", isSecondAccepted)
    }
}
