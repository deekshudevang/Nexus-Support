package com.meshlink.app.mesh.routing

import com.meshlink.app.mesh.util.BloomFilterDedup
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid deduplication cache for mesh packet loop prevention.
 *
 * Two-layer design (inspired by RESCUE-MESH's BloomFilter approach):
 *   1. **BloomFilter** — space-efficient probabilistic check (~4KB for 10,000 entries).
 *      Catches the vast majority of duplicates with zero per-entry overhead.
 *   2. **LRU cache** — small exact-match set of the most recent 200 messageIds.
 *      Rescues false positives from the Bloom filter for very recent traffic.
 *
 * Each entry in the LRU also carries a TTL. Entries older than 10 minutes are
 * treated as unseen, allowing retransmission after long offline periods.
 *
 * Thread safety: all public methods are @Synchronized.
 */
@Singleton
class SeenMessageCache @Inject constructor() {

    companion object {
        private const val LRU_CAPACITY   = 200
        private const val DEFAULT_TTL_MS = 10 * 60 * 1_000L  // 10 minutes
    }

    private val bloomFilter = BloomFilterDedup(expectedInsertions = 10_000, falsePositiveRate = 0.01)

    private data class Entry(val seenAt: Long)

    private val lruCache = object : LinkedHashMap<String, Entry>(
        LRU_CAPACITY + 1, 0.75f, false
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Entry>): Boolean =
            size > LRU_CAPACITY
    }

    /**
     * Returns true if this [messageId] was likely seen recently.
     * Uses Bloom filter first, then falls back to LRU for exact check.
     */
    @Synchronized
    fun isAlreadySeen(messageId: String): Boolean {
        // Fast check: LRU exact match (handles TTL expiry)
        val entry = lruCache[messageId]
        if (entry != null) {
            val age = System.currentTimeMillis() - entry.seenAt
            if (age > DEFAULT_TTL_MS) {
                lruCache.remove(messageId)
                // Don't return false yet — Bloom may still catch it
            } else {
                return true
            }
        }
        // Probabilistic check: Bloom filter (no TTL, but auto-rotates at capacity)
        return bloomFilter.mightContain(messageId)
    }

    /**
     * Records [messageId] as seen right now.
     */
    @Synchronized
    fun markSeen(messageId: String) {
        lruCache[messageId] = Entry(seenAt = System.currentTimeMillis())
        bloomFilter.put(messageId)
        Timber.v("SeenCache: marked seen $messageId (lru=${lruCache.size})")
    }
}
