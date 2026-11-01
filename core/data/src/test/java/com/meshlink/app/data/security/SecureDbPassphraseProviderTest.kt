package com.meshlink.app.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureDbPassphraseProviderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("meshlink_db_secure_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("meshlink_keyset_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun getOrGeneratePassphrase_generatesHex256BitPassphrase() {
        val passphrase = SecureDbPassphraseProvider.getOrGeneratePassphrase(context)
        val passphraseString = String(passphrase, Charsets.UTF_8)

        // 32 bytes hex encoded = 64 characters
        assertEquals(64, passphraseString.length)
        assertTrue(passphraseString.all { it in "0123456789abcdef" })
    }

    @Test
    fun getOrGeneratePassphrase_isIdempotent() {
        val first = SecureDbPassphraseProvider.getOrGeneratePassphrase(context)
        val second = SecureDbPassphraseProvider.getOrGeneratePassphrase(context)

        assertArrayEquals(first, second)
    }

    @Test
    fun rawPrefs_storesCiphertextNotPlaintext() {
        val passphrase = SecureDbPassphraseProvider.getOrGeneratePassphrase(context)
        val passphraseString = String(passphrase, Charsets.UTF_8)

        val rawPrefs = context.getSharedPreferences("meshlink_db_secure_prefs", Context.MODE_PRIVATE)
        val storedRaw = rawPrefs.getString("db_passphrase", null)

        assertNotNull(storedRaw)
        assertNotEquals(passphraseString, storedRaw)
    }

    @Test
    fun migrateLegacyPassphrase_preservesExistingDbPassphrase() {
        // Mock legacy shared_prefs file and preference
        val legacySharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        legacySharedPrefsDir.mkdirs()
        val legacyPrefsFile = File(legacySharedPrefsDir, "meshlink_db_prefs.xml")
        legacyPrefsFile.writeText("<map></map>")

        val legacyPrefs = context.getSharedPreferences("meshlink_db_prefs", Context.MODE_PRIVATE)
        val legacyPassphrase = "legacy_secret_passphrase_to_preserve_user_db"
        legacyPrefs.edit().putString("db_passphrase", legacyPassphrase).commit()

        val retrievedPassphrase = SecureDbPassphraseProvider.getOrGeneratePassphrase(context)
        assertEquals(legacyPassphrase, String(retrievedPassphrase, Charsets.UTF_8))

        // Ensure it is now persisted in Tink encrypted prefs
        val rawPrefs = context.getSharedPreferences("meshlink_db_secure_prefs", Context.MODE_PRIVATE)
        assertNotNull(rawPrefs.getString("db_passphrase", null))

        // Clean up
        legacyPrefsFile.delete()
    }
}
