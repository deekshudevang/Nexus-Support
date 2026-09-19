package com.meshlink.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.meshlink.app.data.local.dao.DeviceDao
import com.meshlink.app.data.local.dao.MessageDao
import com.meshlink.app.data.local.dao.PendingMessageDao
import com.meshlink.app.data.local.entity.KnownDeviceEntity
import com.meshlink.app.data.local.entity.LocationEventEntity
import com.meshlink.app.data.local.entity.LocationSyncQueueEntity
import com.meshlink.app.data.local.entity.MessageEntity
import com.meshlink.app.data.local.entity.PendingMessageEntity
import com.meshlink.app.data.local.entity.ProcessedEventEntity

@Database(
    entities = [
        KnownDeviceEntity::class,
        MessageEntity::class,
        PendingMessageEntity::class,
        LocationEventEntity::class,
        LocationSyncQueueEntity::class,
        ProcessedEventEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun deviceDao(): DeviceDao
    abstract fun pendingMessageDao(): PendingMessageDao
    abstract fun locationEventDao(): com.meshlink.app.data.local.dao.LocationEventDao
    abstract fun locationSyncQueueDao(): com.meshlink.app.data.local.dao.LocationSyncQueueDao
    abstract fun processedEventDao(): com.meshlink.app.data.local.dao.ProcessedEventDao

    companion object {
        /**
         * v1 → v2: adds [pending_messages] table for store-and-forward queuing.
         * Pure additive migration — no existing data is affected.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_messages (
                        id              TEXT    NOT NULL PRIMARY KEY,
                        packetJson      TEXT    NOT NULL,
                        targetDeviceId  TEXT    NOT NULL,
                        enqueuedAt      INTEGER NOT NULL,
                        expiresAt       INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pending_messages_targetDeviceId " +
                    "ON pending_messages (targetDeviceId)"
                )
            }
        }

        /**
         * v2 → v3: adds senderName column to messages table for user identity display.
         * Pure additive migration — existing messages get empty string as default.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN senderName TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * v3 → v4: adds routeHistory column to messages table for multi-hop UI path display.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN routeHistory TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * v4 → v5: adds priority to pending_messages and status to messages table.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_messages ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN status INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v5 → v6: Empty migration to sync Room's internal schema hash after fixing
         * the missing @ColumnInfo(defaultValue="0") on priority.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes required
            }
        }

        /**
         * v8 → v9: adds lastLatitude and lastLongitude to known_devices.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE known_devices ADD COLUMN lastLatitude REAL")
                db.execSQL("ALTER TABLE known_devices ADD COLUMN lastLongitude REAL")
            }
        }

        /**
         * v9 → v10: adds location_events and location_sync_queue tables
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS location_events (
                        eventId TEXT NOT NULL PRIMARY KEY,
                        peerId TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        accuracy REAL NOT NULL,
                        timestamp INTEGER NOT NULL,
                        sequenceNumber INTEGER NOT NULL,
                        signature TEXT NOT NULL,
                        syncStatus INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS location_sync_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventId TEXT NOT NULL,
                        targetPeerId TEXT NOT NULL,
                        ttl INTEGER NOT NULL,
                        status INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(eventId) REFERENCES location_events(eventId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v10 → v11: adds processed_events table for deduplication
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS processed_events (
                        eventId TEXT NOT NULL PRIMARY KEY,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        /**
         * v11 → v12: adds publicKey column to location_events for cryptographic verification
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE location_events ADD COLUMN publicKey TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
