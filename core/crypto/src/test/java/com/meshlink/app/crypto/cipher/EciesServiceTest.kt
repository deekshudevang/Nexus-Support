package com.meshlink.app.crypto.cipher

import com.meshlink.app.crypto.identity.KeyProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class EciesServiceTest {

    private lateinit var encryptionService: EncryptionService
    private lateinit var eciesService: EciesService
    private lateinit var keyManagerMock: KeyProvider
    
    // Simulates the local device's identity keypair
    private val localKeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    
    // Simulates a remote peer's public key (to send to)
    private val remoteKeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    @Before
    fun setup() {
        encryptionService = EncryptionService()
        keyManagerMock = mockk()
        
        every { keyManagerMock.keyPair } returns localKeyPair
        every { keyManagerMock.publicKeyBytes } returns localKeyPair.public.encoded
        
        eciesService = EciesService(keyManagerMock, encryptionService)
    }

    @Test
    fun `encrypt and decrypt should return original plaintext`() {
        val plaintext = "Multi-hop Secret Payload".toByteArray(Charsets.UTF_8)
        
        // Simulating Remote Peer encrypting a message for Local Device
        // The sender needs Local Device's public key
        val encryptedWireBytes = eciesService.encrypt(plaintext, localKeyPair.public.encoded)
        
        // Local Device decrypts it using its own KeyManager (which provides localKeyPair.private)
        val decrypted = eciesService.decrypt(encryptedWireBytes)
        
        assertNotNull("Decrypted bytes should not be null", decrypted)
        assertArrayEquals("Decrypted bytes should match original plaintext", plaintext, decrypted)
    }

    @Test
    fun `decrypt with wrong private key should return null`() {
        val plaintext = "Intercepted".toByteArray(Charsets.UTF_8)
        
        // Sender encrypts for Remote Peer
        val encryptedWireBytes = eciesService.encrypt(plaintext, remoteKeyPair.public.encoded)
        
        // Local Device tries to decrypt it (using Local Device's private key, which is wrong)
        val decrypted = eciesService.decrypt(encryptedWireBytes)
        
        assertNull("Decryption with wrong private key should fail and return null", decrypted)
    }

    @Test
    fun `tampered payload should fail decryption`() {
        val plaintext = "Tamper me".toByteArray(Charsets.UTF_8)
        val encryptedWireBytes = eciesService.encrypt(plaintext, localKeyPair.public.encoded)
        
        // Modify a byte in the payload
        encryptedWireBytes[encryptedWireBytes.size - 5] = (encryptedWireBytes[encryptedWireBytes.size - 5] + 1).toByte()
        
        val decrypted = eciesService.decrypt(encryptedWireBytes)
        assertNull("Tampered payload should fail GCM authentication and return null", decrypted)
    }
}
