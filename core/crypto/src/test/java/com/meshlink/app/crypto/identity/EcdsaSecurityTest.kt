package com.meshlink.app.crypto.identity

import android.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

class EcdsaSecurityTest {

    private lateinit var keyPairA: KeyPair
    private lateinit var keyPairB: KeyPair

    @Before
    fun setup() {
        io.mockk.mockkStatic(android.util.Base64::class)
        io.mockk.every { android.util.Base64.encodeToString(any<ByteArray>(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        io.mockk.every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        io.mockk.every { android.util.Base64.decode(any<ByteArray>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<ByteArray>())
        }

        val kpg = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }
        keyPairA = kpg.generateKeyPair()
        keyPairB = kpg.generateKeyPair()
    }

    private fun signData(data: ByteArray, keyPair: KeyPair): String {
        val sig = Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyPair.private)
            update(data)
        }
        return Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
    }

    private fun verifySignature(data: ByteArray, signatureBase64: String, publicKeyBytes: ByteArray): Boolean {
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val sig = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(pubKey)
                update(data)
            }
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            sig.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }

    @Test
    fun `positive signature generation and verification on authentic payload`() {
        val payload = "EVENT_ID:1234|ORIGIN:NODE_A|LAT:12.9716|LON:77.5946|TS:1700000000".toByteArray(Charsets.UTF_8)
        val signature = signData(payload, keyPairA)

        assertNotNull(signature)
        assertTrue("Authentic signature must verify successfully", verifySignature(payload, signature, keyPairA.public.encoded))
    }

    @Test
    fun `tampered payload fails ECDSA signature verification`() {
        val authenticPayload = "EMERGENCY_SOS_LOCATION:12.9716,77.5946".toByteArray(Charsets.UTF_8)
        val signature = signData(authenticPayload, keyPairA)

        // Adversary alters coordinates in transit
        val forgedPayload = "EMERGENCY_SOS_LOCATION:12.9999,77.9999".toByteArray(Charsets.UTF_8)

        assertFalse("Tampered payload must fail signature verification", verifySignature(forgedPayload, signature, keyPairA.public.encoded))
    }

    @Test
    fun `signature from mismatched key fails verification`() {
        val payload = "SOS message signed by Node B masquerading as Node A".toByteArray(Charsets.UTF_8)
        // Signed by KeyPair B
        val signatureFromB = signData(payload, keyPairB)

        // Receiver verifies against KeyPair A's registered public key
        assertFalse("Signature signed by impostor key must fail verification", verifySignature(payload, signatureFromB, keyPairA.public.encoded))
    }

    @Test
    fun `corrupted signature bytes fails verification gracefully`() {
        val payload = "Legitimate emergency payload".toByteArray(Charsets.UTF_8)
        val validSignature = signData(payload, keyPairA)

        // Corrupt signature string
        val corruptedSig = validSignature.substring(0, validSignature.length - 4) + "AAAA"

        assertFalse("Corrupted signature must fail verification gracefully", verifySignature(payload, corruptedSig, keyPairA.public.encoded))
    }

    @Test
    fun `ECDH shared secret computation produces identical 32-byte secret between two NIST P-256 nodes`() {
        val kaA = KeyAgreement.getInstance("ECDH").apply {
            init(keyPairA.private)
            doPhase(keyPairB.public, true)
        }
        val secretA = kaA.generateSecret()

        val kaB = KeyAgreement.getInstance("ECDH").apply {
            init(keyPairB.private)
            doPhase(keyPairA.public, true)
        }
        val secretB = kaB.generateSecret()

        assertEquals("Shared secret must be 32 bytes for P-256", 32, secretA.size)
        assertArrayEquals("Both peers must compute the exact same ECDH shared secret", secretA, secretB)
    }
}
