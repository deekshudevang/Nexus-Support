package com.meshlink.app.crypto.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TinkSecureStorageTest {

    private lateinit var context: Context
    private lateinit var storage: TinkSecureStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = TinkSecureStorage(context, "test_secure_storage", "test_master_key")
        storage.clear()
    }

    @Test
    fun putAndGetString_returnsDecryptedPlaintext() {
        storage.putString("auth_token", "super_secret_payload_12345")
        
        val retrieved = storage.getString("auth_token")
        assertEquals("super_secret_payload_12345", retrieved)
    }

    @Test
    fun rawSharedPreferences_storesCiphertextNotPlaintext() {
        storage.putString("secret_key", "plaintext_secret_value")

        val rawPrefs = context.getSharedPreferences("test_secure_storage", Context.MODE_PRIVATE)
        val rawStoredValue = rawPrefs.getString("secret_key", null)

        assertNotNull(rawStoredValue)
        assertNotEquals("plaintext_secret_value", rawStoredValue)
    }

    @Test
    fun getString_missingKey_returnsDefaultValue() {
        val result = storage.getString("non_existent_key", "fallback_default")
        assertEquals("fallback_default", result)
    }

    @Test
    fun containsAndRemove_workCorrectly() {
        storage.putString("temp_key", "temp_value")
        assertTrue(storage.contains("temp_key"))

        storage.remove("temp_key")
        assertFalse(storage.contains("temp_key"))
        assertNull(storage.getString("temp_key"))
    }

    @Test
    fun clear_removesAllEntries() {
        storage.putString("key1", "val1")
        storage.putString("key2", "val2")

        storage.clear()

        assertNull(storage.getString("key1"))
        assertNull(storage.getString("key2"))
    }
}
