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
    context: Context? = null
) {
    private val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private val memoryCounter = AtomicInteger(0)

    @Synchronized
    fun nextNonce(): Byte {
        val counter = if (prefs != null) {
            val current = prefs.getInt(KEY_COUNTER, 0)
            prefs.edit().putInt(KEY_COUNTER, current + 1).apply()
            current
        } else {
            memoryCounter.getAndIncrement()
        }
        val randomByte = ByteArray(1).also { random.nextBytes(it) }[0]
        return ((counter and 0xFF) xor randomByte.toInt()).toByte()
    }

    companion object {
        private const val PREFS_NAME = "meshlink_sos_nonce"
        private const val KEY_COUNTER = "activation_counter"
    }
}
