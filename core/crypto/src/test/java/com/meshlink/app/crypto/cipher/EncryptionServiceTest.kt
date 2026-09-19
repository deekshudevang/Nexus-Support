package com.meshlink.app.crypto.cipher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class EncryptionServiceTest {

    private lateinit var encryptionService: EncryptionService
    private lateinit var secretKey: SecretKey

    @Before
    fun setup() {
        encryptionService = EncryptionService()
        
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        secretKey = keyGen.generateKey()
    }

    @Test
    fun `encrypt and decrypt should return original plaintext`() {
        val plaintext = "Hello Meshlink".toByteArray(Charsets.UTF_8)
        
        val encrypted = encryptionService.encrypt(plaintext, secretKey)
        val decrypted = encryptionService.decrypt(encrypted, secretKey)
        
        assertNotNull("Decrypted bytes should not be null", decrypted)
        assertArrayEquals("Decrypted bytes should match original plaintext", plaintext, decrypted)
    }

    @Test
    fun `decrypt with wrong key should return null`() {
        val plaintext = "Top Secret".toByteArray(Charsets.UTF_8)
        val encrypted = encryptionService.encrypt(plaintext, secretKey)
        
        val wrongKeyGen = KeyGenerator.getInstance("AES")
        wrongKeyGen.init(256)
        val wrongKey = wrongKeyGen.generateKey()
        
        val decrypted = encryptionService.decrypt(encrypted, wrongKey)
        assertNull("Decryption with wrong key should fail and return null", decrypted)
    }

    @Test
    fun `tampered ciphertext should fail GCM authentication and return null`() {
        val plaintext = "Will be tampered".toByteArray(Charsets.UTF_8)
        val encrypted = encryptionService.encrypt(plaintext, secretKey)
        
        // Modify a single byte in the ciphertext part
        encrypted[encrypted.size - 17] = (encrypted[encrypted.size - 17] + 1).toByte()
        
        val decrypted = encryptionService.decrypt(encrypted, secretKey)
        assertNull("Tampered message should fail GCM authentication", decrypted)
    }

    @Test
    fun `tampered auth tag should fail GCM authentication and return null`() {
        val plaintext = "Tag tampered".toByteArray(Charsets.UTF_8)
        val encrypted = encryptionService.encrypt(plaintext, secretKey)
        
        // Modify the very last byte (part of the 16-byte GCM tag)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1] + 1).toByte()
        
        val decrypted = encryptionService.decrypt(encrypted, secretKey)
        assertNull("Tampered tag should fail authentication", decrypted)
    }
}
