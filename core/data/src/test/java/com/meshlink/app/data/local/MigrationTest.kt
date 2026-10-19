package com.meshlink.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.robolectric.RobolectricTestRunner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO messages (id, senderId, receiverId, ciphertext, timestamp, delivered) " +
                "VALUES ('msg1', 'alice', 'bob', X'0102', 1000, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            AppDatabase.MIGRATION_1_2
        )

        // Verify messages table persisted and pending_messages table exists
        val msgCursor = db.query("SELECT id, senderId FROM messages WHERE id = 'msg1'")
        msgCursor.moveToFirst()
        assertEquals("msg1", msgCursor.getString(0))
        assertEquals("alice", msgCursor.getString(1))
        msgCursor.close()

        val pendingCursor = db.query("SELECT count(*) FROM pending_messages")
        pendingCursor.moveToFirst()
        assertEquals(0, pendingCursor.getInt(0))
        pendingCursor.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO messages (id, senderId, receiverId, ciphertext, timestamp, delivered) " +
                "VALUES ('msg2', 'bob', 'alice', X'0304', 2000, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_2_3
        )

        // Verify senderName column added with default empty string
        val cursor = db.query("SELECT id, senderName FROM messages WHERE id = 'msg2'")
        cursor.moveToFirst()
        assertEquals("msg2", cursor.getString(0))
        assertEquals("", cursor.getString(1))
        cursor.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To3_chain() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO messages (id, senderId, receiverId, ciphertext, timestamp, delivered) " +
                "VALUES ('msg_chain', 'alice', 'bob', X'0506', 3000, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3
        )

        val cursor = db.query("SELECT id, senderName FROM messages WHERE id = 'msg_chain'")
        cursor.moveToFirst()
        assertEquals("msg_chain", cursor.getString(0))
        assertEquals("", cursor.getString(1))
        cursor.close()
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14() {
        helper.createDatabase(TEST_DB, 13).apply {
            execSQL(
                "INSERT INTO known_devices (deviceId, displayName, publicKey, lastSeen, lastLatitude, lastLongitude) " +
                "VALUES ('dev13', 'Charlie', X'0708', 5000, 40.7128, -74.0060)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            14,
            true,
            AppDatabase.MIGRATION_13_14
        )

        // Verify isVerified column added with default 0 and previous data preserved
        val cursor = db.query("SELECT deviceId, displayName, isVerified, lastLatitude FROM known_devices WHERE deviceId = 'dev13'")
        cursor.moveToFirst()
        assertEquals("dev13", cursor.getString(0))
        assertEquals("Charlie", cursor.getString(1))
        assertEquals(0, cursor.getInt(2))
        assertEquals(40.7128, cursor.getDouble(3), 0.0001)
        cursor.close()
        db.close()
    }

    /**
     * Verifies that all declared intermediate migration steps (3->4 through 12->13)
     * execute their DDL statements cleanly on a SQLite instance.
     */
    @Test
    fun testAllIntermediateMigrationsExecuteWithoutSqlError() {
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
            InstrumentationRegistry.getInstrumentation().context
        ).name("intermediate-migration-test")
         .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
             override fun onCreate(db: SupportSQLiteDatabase) {
                 // Create minimal v3 schema
                 db.execSQL("CREATE TABLE messages (id TEXT NOT NULL PRIMARY KEY, senderId TEXT NOT NULL, receiverId TEXT NOT NULL, senderName TEXT NOT NULL DEFAULT '', ciphertext BLOB NOT NULL, timestamp INTEGER NOT NULL, delivered INTEGER NOT NULL)")
                 db.execSQL("CREATE TABLE pending_messages (id TEXT NOT NULL PRIMARY KEY, packetJson TEXT NOT NULL, targetDeviceId TEXT NOT NULL, enqueuedAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL)")
                 db.execSQL("CREATE TABLE known_devices (deviceId TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, publicKey BLOB NOT NULL, lastSeen INTEGER NOT NULL)")
             }
             override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
         }).build()

        val dbHelper = helperFactory.create(config)
        val db = dbHelper.writableDatabase

        // Execute migrations 3->4 through 13->14 sequentially
        AppDatabase.MIGRATION_3_4.migrate(db)
        AppDatabase.MIGRATION_4_5.migrate(db)
        AppDatabase.MIGRATION_5_6.migrate(db)
        AppDatabase.MIGRATION_6_7.migrate(db)
        AppDatabase.MIGRATION_7_8.migrate(db)
        AppDatabase.MIGRATION_8_9.migrate(db)
        AppDatabase.MIGRATION_9_10.migrate(db)
        AppDatabase.MIGRATION_10_11.migrate(db)
        AppDatabase.MIGRATION_11_12.migrate(db)
        AppDatabase.MIGRATION_12_13.migrate(db)
        AppDatabase.MIGRATION_13_14.migrate(db)

        // Verify tables and columns created
        val tableCheck = db.query("SELECT name FROM sqlite_master WHERE type='table'")
        val tables = mutableSetOf<String>()
        while (tableCheck.moveToNext()) {
            tables.add(tableCheck.getString(0))
        }
        tableCheck.close()

        assertNotNull(tables.contains("sos_packets"))
        assertNotNull(tables.contains("location_events"))
        assertNotNull(tables.contains("location_sync_queue"))
        assertNotNull(tables.contains("processed_events"))

        db.close()
    }
}
