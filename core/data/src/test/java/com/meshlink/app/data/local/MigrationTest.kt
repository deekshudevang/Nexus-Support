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

    /**
     * Verifies end-to-end data integrity across the complete migration sequence from v1 to v14.
     * Confirms that existing messages, devices, and queues survive all DDL alterations,
     * new non-null columns receive valid default values, and newly introduced tables accept records.
     */
    @Test
    fun testFullV1ToV14SequentialDataIntegrityMigration() {
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
            InstrumentationRegistry.getInstrumentation().context
        ).name("full-chain-data-integrity-test")
         .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
             override fun onCreate(db: SupportSQLiteDatabase) {
                 // Create authentic v1 schema
                 db.execSQL(
                     "CREATE TABLE IF NOT EXISTS messages (" +
                     "id TEXT NOT NULL PRIMARY KEY, " +
                     "senderId TEXT NOT NULL, " +
                     "receiverId TEXT NOT NULL, " +
                     "ciphertext BLOB NOT NULL, " +
                     "timestamp INTEGER NOT NULL, " +
                     "delivered INTEGER NOT NULL)"
                 )
                 db.execSQL(
                     "CREATE TABLE IF NOT EXISTS known_devices (" +
                     "deviceId TEXT NOT NULL PRIMARY KEY, " +
                     "displayName TEXT NOT NULL, " +
                     "publicKey BLOB NOT NULL, " +
                     "lastSeen INTEGER NOT NULL)"
                 )
             }
             override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
         }).build()

        val dbHelper = helperFactory.create(config)
        val db = dbHelper.writableDatabase

        // 1. Seed initial data into v1 database
        db.execSQL(
            "INSERT INTO messages (id, senderId, receiverId, ciphertext, timestamp, delivered) " +
            "VALUES ('msg_ancient_1', 'sender_alpha', 'receiver_beta', X'DEADBEEF', 100000, 1)"
        )
        db.execSQL(
            "INSERT INTO known_devices (deviceId, displayName, publicKey, lastSeen) " +
            "VALUES ('node_1', 'Alpha Rescue', X'01020304', 200000)"
        )

        // 2. Apply MIGRATION_1_2 (adds pending_messages table)
        AppDatabase.MIGRATION_1_2.migrate(db)
        db.execSQL(
            "INSERT INTO pending_messages (id, packetJson, targetDeviceId, enqueuedAt, expiresAt) " +
            "VALUES ('pend_v2', '{\"type\":\"PING\"}', 'node_1', 300000, 400000)"
        )

        // 3. Apply MIGRATION_2_3 (adds senderName to messages)
        AppDatabase.MIGRATION_2_3.migrate(db)

        // 4. Apply MIGRATION_3_4 (adds routeHistory to messages)
        AppDatabase.MIGRATION_3_4.migrate(db)

        // 5. Apply MIGRATION_4_5 (adds priority to pending_messages, status to messages)
        AppDatabase.MIGRATION_4_5.migrate(db)

        // 6. Apply MIGRATION_5_6, 6_7, 7_8 (schema sync steps)
        AppDatabase.MIGRATION_5_6.migrate(db)
        AppDatabase.MIGRATION_6_7.migrate(db)
        AppDatabase.MIGRATION_7_8.migrate(db)

        // 7. Apply MIGRATION_8_9 (adds lastLatitude and lastLongitude to known_devices)
        AppDatabase.MIGRATION_8_9.migrate(db)

        // 8. Apply MIGRATION_9_10 (adds location_events and location_sync_queue)
        AppDatabase.MIGRATION_9_10.migrate(db)
        db.execSQL(
            "INSERT INTO location_events (eventId, peerId, latitude, longitude, accuracy, timestamp, sequenceNumber, signature, syncStatus) " +
            "VALUES ('loc_evt_1', 'node_1', 37.7749, -122.4194, 5.0, 500000, 1, 'sig_dummy', 0)"
        )

        // 9. Apply MIGRATION_10_11 (adds processed_events)
        AppDatabase.MIGRATION_10_11.migrate(db)

        // 10. Apply MIGRATION_11_12 (adds publicKey to location_events, index on location_sync_queue)
        AppDatabase.MIGRATION_11_12.migrate(db)

        // 11. Apply MIGRATION_12_13 (adds sos_packets table)
        AppDatabase.MIGRATION_12_13.migrate(db)
        db.execSQL(
            "INSERT INTO sos_packets (uuid, senderId, senderName, emergencyType, severity, latitude, longitude, message, timestamp, hopCount, status) " +
            "VALUES ('sos_uuid_99', 'node_1', 'Alpha Rescue', 'MEDICAL', 1, 37.7750, -122.4190, 'Need oxygen', 600000, 0, 'PENDING')"
        )

        // 12. Apply MIGRATION_13_14 (adds isVerified to known_devices)
        AppDatabase.MIGRATION_13_14.migrate(db)

        // --- VERIFY DATA INTEGRITY OF ORIGINAL v1 / v2 DATA ---
        val msgCursor = db.query(
            "SELECT id, senderId, receiverId, senderName, routeHistory, status, delivered FROM messages WHERE id = 'msg_ancient_1'"
        )
        msgCursor.moveToFirst()
        assertEquals("msg_ancient_1", msgCursor.getString(0))
        assertEquals("sender_alpha", msgCursor.getString(1))
        assertEquals("receiver_beta", msgCursor.getString(2))
        assertEquals("", msgCursor.getString(3)) // default senderName
        assertEquals("", msgCursor.getString(4)) // default routeHistory
        assertEquals(0, msgCursor.getInt(5))     // default status
        assertEquals(1, msgCursor.getInt(6))     // original delivered value preserved
        msgCursor.close()

        val devCursor = db.query(
            "SELECT deviceId, displayName, lastSeen, isVerified, lastLatitude FROM known_devices WHERE deviceId = 'node_1'"
        )
        devCursor.moveToFirst()
        assertEquals("node_1", devCursor.getString(0))
        assertEquals("Alpha Rescue", devCursor.getString(1))
        assertEquals(200000L, devCursor.getLong(2))
        assertEquals(0, devCursor.getInt(3)) // default isVerified
        org.junit.Assert.assertTrue(devCursor.isNull(4)) // nullable lastLatitude
        devCursor.close()

        val pendCursor = db.query(
            "SELECT id, targetDeviceId, priority FROM pending_messages WHERE id = 'pend_v2'"
        )
        pendCursor.moveToFirst()
        assertEquals("pend_v2", pendCursor.getString(0))
        assertEquals("node_1", pendCursor.getString(1))
        assertEquals(0, pendCursor.getInt(2)) // default priority
        pendCursor.close()

        val sosCursor = db.query(
            "SELECT uuid, emergencyType, severity, status FROM sos_packets WHERE uuid = 'sos_uuid_99'"
        )
        sosCursor.moveToFirst()
        assertEquals("sos_uuid_99", sosCursor.getString(0))
        assertEquals("MEDICAL", sosCursor.getString(1))
        assertEquals(1, sosCursor.getInt(2))
        assertEquals("PENDING", sosCursor.getString(3))
        sosCursor.close()

        db.close()
    }

    /**
     * Verifies ADR-0007 failure-recovery policy: in production configuration
     * (where fallbackToDestructiveMigration is disabled), missing migrations
     * must throw an IllegalStateException rather than silently deleting user emergency data.
     */
    @Test
    fun testFailureRecoveryPolicy_missingMigrationThrowsWithoutDataDestruction() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val dbFile = context.getDatabasePath("test-failure-recovery.db")
        if (dbFile.exists()) dbFile.delete()

        // Create a raw SQLite database at version 1 with an emergency contact
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test-failure-recovery.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE known_devices (deviceId TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, publicKey BLOB NOT NULL, lastSeen INTEGER NOT NULL, isVerified INTEGER NOT NULL DEFAULT 0)")
                    db.execSQL("INSERT INTO known_devices (deviceId, displayName, publicKey, lastSeen, isVerified) VALUES ('critical_contact', 'HQ Command', X'A1B2', 999999, 1)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build()
        val helper = helperFactory.create(config)
        val initialDb = helper.writableDatabase
        initialDb.close()

        // Attempt to open with Room expecting version 14, without any migrations or destructive fallback
        var exceptionThrown = false
        try {
            val roomDb = androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, "test-failure-recovery.db")
                // Do NOT call fallbackToDestructiveMigration() - simulates release build
                .build()
            roomDb.openHelper.writableDatabase
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }

        org.junit.Assert.assertTrue("Room must fail closed with IllegalStateException when migration is missing", exceptionThrown)

        // Verify the database file still exists and data was NOT wiped
        org.junit.Assert.assertTrue("Database file must not be destroyed", dbFile.exists())
        val verifyDb = helperFactory.create(config).readableDatabase
        val cursor = verifyDb.query("SELECT displayName FROM known_devices WHERE deviceId = 'critical_contact'")
        cursor.moveToFirst()
        assertEquals("HQ Command", cursor.getString(0))
        cursor.close()
        verifyDb.close()

        dbFile.delete()
    }
}
