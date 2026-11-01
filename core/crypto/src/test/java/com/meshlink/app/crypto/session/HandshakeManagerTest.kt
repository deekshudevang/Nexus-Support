package com.meshlink.app.crypto.session

import android.util.Base64
import com.meshlink.app.crypto.identity.KeyProvider
import com.meshlink.app.domain.model.KnownDevice
import com.meshlink.app.domain.model.MeshPacket
import com.meshlink.app.domain.repository.DeviceRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

class HandshakeManagerTest {

    private lateinit var keyManagerA: KeyProvider
    private lateinit var keyManagerB: KeyProvider
    private lateinit var sessionKeyStoreA: SessionKeyStore
    private lateinit var sessionKeyStoreB: SessionKeyStore
    private lateinit var deviceRepositoryA: DeviceRepository
    private lateinit var deviceRepositoryB: DeviceRepository

    private lateinit var handshakeManagerA: HandshakeManager
    private lateinit var handshakeManagerB: HandshakeManager

    private lateinit var keyPairA: KeyPair
    private lateinit var keyPairB: KeyPair

    @Before
    fun setup() {
        io.mockk.mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any<ByteArray>(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        every { android.util.Base64.decode(any<ByteArray>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<ByteArray>())
        }

        val kpg = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }
        keyPairA = kpg.generateKeyPair()
        keyPairB = kpg.generateKeyPair()

        keyManagerA = mockk()
        every { keyManagerA.keyPair } returns keyPairA
        every { keyManagerA.publicKeyBytes } returns keyPairA.public.encoded
        every { keyManagerA.computeSharedSecret(any()) } answers {
            val peerBytes = firstArg<ByteArray>()
            val peerPub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerBytes))
            KeyAgreement.getInstance("ECDH").apply {
                init(keyPairA.private)
                doPhase(peerPub, true)
            }.generateSecret()
        }

        keyManagerB = mockk()
        every { keyManagerB.keyPair } returns keyPairB
        every { keyManagerB.publicKeyBytes } returns keyPairB.public.encoded
        every { keyManagerB.computeSharedSecret(any()) } answers {
            val peerBytes = firstArg<ByteArray>()
            val peerPub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerBytes))
            KeyAgreement.getInstance("ECDH").apply {
                init(keyPairB.private)
                doPhase(peerPub, true)
            }.generateSecret()
        }

        sessionKeyStoreA = SessionKeyStore()
        sessionKeyStoreB = SessionKeyStore()

        deviceRepositoryA = mockk(relaxed = true)
        deviceRepositoryB = mockk(relaxed = true)

        handshakeManagerA = HandshakeManager(keyManagerA, sessionKeyStoreA, deviceRepositoryA)
        handshakeManagerB = HandshakeManager(keyManagerB, sessionKeyStoreB, deviceRepositoryB)
    }

    @Test
    fun `createHandshakePacket produces valid handshake packet with own public key`() {
        val packet = handshakeManagerA.createHandshakePacket("DEVICE_A", "EP_B")
        assertEquals("DEVICE_A", packet.senderId)
        assertEquals("EP_B", packet.receiverId)
        assertEquals(MeshPacket.PacketType.HANDSHAKE, packet.type)
        assertNotNull(packet.content)
        val decoded = Base64.decode(packet.content, Base64.NO_WRAP)
        assertTrue(keyPairA.public.encoded.contentEquals(decoded))
    }

    @Test
    fun `bidirectional handshake succeeds and derives identical AES-256 session key on both sides`() = runTest {
        coEvery { deviceRepositoryA.getDeviceById("DEVICE_B") } returns null
        coEvery { deviceRepositoryB.getDeviceById("DEVICE_A") } returns null

        val packetAtoB = handshakeManagerA.createHandshakePacket("DEVICE_A", "EP_B")
        val packetBtoA = handshakeManagerB.createHandshakePacket("DEVICE_B", "EP_A")

        // Node B processes packet from A
        val successB = handshakeManagerB.processHandshake("EP_A", "DEVICE_A", packetAtoB.content)
        assertTrue("Node B handshake should succeed", successB)
        assertTrue(handshakeManagerB.isHandshakeComplete("EP_A"))

        // Node A processes packet from B
        val successA = handshakeManagerA.processHandshake("EP_B", "DEVICE_B", packetBtoA.content)
        assertTrue("Node A handshake should succeed", successA)
        assertTrue(handshakeManagerA.isHandshakeComplete("EP_B"))

        // Verify identical session keys derived on both ends
        val keyOnA = sessionKeyStoreA.getSessionKey("EP_B")
        val keyOnB = sessionKeyStoreB.getSessionKey("EP_A")
        assertNotNull(keyOnA)
        assertNotNull(keyOnB)
        assertEquals("AES", keyOnA!!.algorithm)
        assertEquals("AES", keyOnB!!.algorithm)
        assertTrue("Both sides must derive identical symmetric session key", keyOnA.encoded.contentEquals(keyOnB.encoded))
    }

    @Test
    fun `computeSAS produces identical 6-digit code on both peers for out-of-band verification`() {
        val sasOnA = handshakeManagerA.computeSAS(keyPairB.public.encoded)
        val sasOnB = handshakeManagerB.computeSAS(keyPairA.public.encoded)

        assertEquals("Both devices must compute identical SAS code", sasOnA, sasOnB)
        assertEquals("SAS code must be exactly 6 digits", 6, sasOnA.length)
        assertTrue("SAS code must be numeric", sasOnA.all { it.isDigit() })
    }

    @Test
    fun `repeat-contact MITM attempt with mismatched public key is rejected and aborts handshake`() = runTest {
        // Device A has previously pinned Device B's authentic public key
        val authenticKnownDevice = KnownDevice(
            deviceId = "DEVICE_B",
            displayName = "Alice",
            publicKey = keyPairB.public.encoded,
            lastSeen = 1000L
        )
        coEvery { deviceRepositoryA.getDeviceById("DEVICE_B") } returns authenticKnownDevice

        // Attacker Eve generates an illegitimate keypair and attempts to masquerade as DEVICE_B
        val eveKeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val evePubKeyBase64 = Base64.encodeToString(eveKeyPair.public.encoded, Base64.NO_WRAP)

        val result = handshakeManagerA.processHandshake("EP_EVE", "DEVICE_B", evePubKeyBase64)
        assertFalse("Handshake must be aborted on public key mismatch (MITM detected)", result)
        assertFalse("Session key must not be stored for untrusted endpoint", sessionKeyStoreA.isHandshakeComplete("EP_EVE"))
    }

    @Test
    fun `handshake fails gracefully on malformed public key payload`() = runTest {
        coEvery { deviceRepositoryA.getDeviceById("DEVICE_CORRUPT") } returns null

        val corruptBase64 = Base64.encodeToString(byteArrayOf(0x00, 0x01, 0x02, 0x03), Base64.NO_WRAP)
        val result = handshakeManagerA.processHandshake("EP_CORRUPT", "DEVICE_CORRUPT", corruptBase64)
        assertFalse("Handshake must return false on invalid public key bytes", result)
    }
}
