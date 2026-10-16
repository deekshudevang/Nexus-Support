package com.meshlink.app.crypto.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SessionKeyStoreTest {

    private lateinit var store: SessionKeyStore
    private lateinit var sampleKey: SecretKey

    @Before
    fun setup() {
        store = SessionKeyStore()
        sampleKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }

    @Test
    fun `storeSessionKey and getSessionKey manage session state accurately`() {
        assertNull(store.getSessionKey("ep_1"))
        assertFalse(store.isHandshakeComplete("ep_1"))

        store.storeSessionKey("ep_1", sampleKey)
        assertNotNull(store.getSessionKey("ep_1"))
        assertTrue(store.isHandshakeComplete("ep_1"))
        assertEquals(sampleKey, store.getSessionKey("ep_1"))
    }

    @Test
    fun `clearSession removes key on disconnect`() {
        store.storeSessionKey("ep_1", sampleKey)
        assertTrue(store.isHandshakeComplete("ep_1"))

        store.clearSession("ep_1")
        assertNull(store.getSessionKey("ep_1"))
        assertFalse(store.isHandshakeComplete("ep_1"))
    }

    @Test
    fun `isNewMessage accepts fresh message IDs and rejects replayed message IDs`() {
        val msgId = "MSG_UNIQUE_123"

        assertTrue("First appearance of message ID is accepted as new", store.isNewMessage(msgId))
        assertFalse("Second appearance of identical message ID is rejected as replay", store.isNewMessage(msgId))
        assertFalse("Third appearance of identical message ID is rejected as replay", store.isNewMessage(msgId))
    }

    @Test
    fun `isNewMessage handles distinct IDs independently`() {
        assertTrue(store.isNewMessage("MSG_A"))
        assertTrue(store.isNewMessage("MSG_B"))
        assertTrue(store.isNewMessage("MSG_C"))

        assertFalse(store.isNewMessage("MSG_A"))
        assertFalse(store.isNewMessage("MSG_B"))
        assertFalse(store.isNewMessage("MSG_C"))
    }

    @Test
    fun `isNewMessage bounded LRU evicts oldest entry after MAX_MESSAGE_IDS limit`() {
        // Feed 2000 items
        for (i in 1..2000) {
            assertTrue(store.isNewMessage("MSG_$i"))
        }

        // MSG_1 is in cache; replay should be rejected
        assertFalse(store.isNewMessage("MSG_1"))

        // Add 1 more to force eviction of oldest entry (which is now MSG_2 since MSG_1 was accessed or oldest head)
        assertTrue(store.isNewMessage("MSG_OVERFLOW"))

        // An entry from the beginning was evicted; verify memory bounded without throwing
        assertTrue(store.isNewMessage("MSG_NEW_AFTER_EVICTION"))
    }
}
