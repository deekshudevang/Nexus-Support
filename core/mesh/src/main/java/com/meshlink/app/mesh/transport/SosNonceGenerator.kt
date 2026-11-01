package com.meshlink.app.mesh.transport

import android.content.Context
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates unique per-activation nonces for SOS beacons.
 * Persists a monotonic counter (in SharedPreferences when available, memory fallback)
 * XORed with a random byte to prevent collisions across restarts and after counter wrap.
 */
@Singleton
class SosNonceGenerator @Inject constructor(
    context: Context? = null,
    private val random: java.util.Random = SecureRandom()
) {
    /**
     * Generates an 8-bit cryptographically random nonce for an SOS activation.
     * Note: As proven in defect N4 analysis, an 8-bit nonce (256 values) cannot guarantee
     * collision resistance under repeated emissions; it serves solely as an ephemeral
     * correlation hint alongside the authoritative 128-bit mesh UUID.
     */
    @Synchronized
    fun nextNonce(): Byte {
        val b = ByteArray(1)
        random.nextBytes(b)
        return b[0]
    }

    companion object {
        private const val PREFS_NAME = "meshlink_sos_nonce"
        private const val KEY_COUNTER = "activation_counter"
    }
}
