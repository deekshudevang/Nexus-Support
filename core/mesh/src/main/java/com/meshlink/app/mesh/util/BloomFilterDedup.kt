package com.meshlink.app.mesh.util

import java.nio.charset.Charset
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Space-efficient probabilistic deduplication filter.
 *
 * Inspired by RESCUE-MESH's BloomFilter — uses MurmurHash3 with configurable
 * false-positive rate. Fixed memory footprint (~4KB for 10,000 entries) vs
 * unbounded HashMap growth.
 *
 * When the filter reaches capacity, [rotateIfNeeded] swaps to a fresh filter.
 * The previous generation is kept briefly for false-positive rescue.
 *
 * Thread safety: all public methods are @Synchronized.
 */
class BloomFilterDedup(
    private val expectedInsertions: Int = 10_000,
    private val falsePositiveRate: Double = 0.01
) {
    // Optimal bit array size: m = -(n * ln(p)) / (ln(2)^2)
    private val bitSize: Int = run {
        val m = -(expectedInsertions * ln(falsePositiveRate)) / (ln(2.0) * ln(2.0))
        maxOf(64, ceil(m).toInt())
    }

    // Optimal number of hash functions: k = (m/n) * ln(2)
    private val hashCount: Int = run {
        val k = (bitSize.toDouble() / expectedInsertions) * ln(2.0)
        maxOf(1, k.roundToInt())
    }

    private var currentBits = BooleanArray(bitSize)
    private var previousBits: BooleanArray? = null
    private var insertionCount = 0

    @Synchronized
    fun mightContain(value: String): Boolean {
        val bytes = value.toByteArray(Charset.forName("UTF-8"))
        // Check current generation
        if (checkBits(currentBits, bytes)) return true
        // Check previous generation (false-positive rescue window)
        val prev = previousBits
        if (prev != null && checkBits(prev, bytes)) return true
        return false
    }

    @Synchronized
    fun put(value: String) {
        val bytes = value.toByteArray(Charset.forName("UTF-8"))
        for (i in 0 until hashCount) {
            val idx = abs(murmurHash3(bytes, i)) % bitSize
            currentBits[idx] = true
        }
        insertionCount++
        rotateIfNeeded()
    }

    private fun checkBits(bits: BooleanArray, bytes: ByteArray): Boolean {
        for (i in 0 until hashCount) {
            val idx = abs(murmurHash3(bytes, i)) % bitSize
            if (!bits[idx]) return false
        }
        return true
    }

    private fun rotateIfNeeded() {
        if (insertionCount >= expectedInsertions) {
            previousBits = currentBits
            currentBits = BooleanArray(bitSize)
            insertionCount = 0
        }
    }

    /**
     * MurmurHash3 32-bit finalizer with seed variation for multiple hash functions.
     */
    private fun murmurHash3(data: ByteArray, seed: Int): Int {
        var h = seed
        for (b in data) {
            h = h xor b.toInt()
            h = (h * 0x5bd1e995)
            h = h xor (h ushr 15)
        }
        h = h xor data.size
        h = h xor (h ushr 16)
        h = (h * 0x85ebca6b.toInt())
        h = h xor (h ushr 13)
        h = (h * 0xc2b2ae35.toInt())
        h = h xor (h ushr 16)
        return h
    }
}
