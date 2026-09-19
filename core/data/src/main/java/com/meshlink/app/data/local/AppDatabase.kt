package com.meshlink.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.meshlink.app.data.local.dao.DeviceDao
import com.meshlink.app.data.local.dao.MessageDao
import com.meshlink.app.data.local.dao.PendingMessageDao
import com.meshlink.app.data.local.entity.KnownDeviceEntity
import com.meshlink.app.data.local.entity.MessageEntity
import com.meshlink.app.data.local.entity.PendingMessageEntity

@Database(
    entities = [
        KnownDeviceEntity::class,
        MessageEntity::class,
        PendingMessageEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun deviceDao(): DeviceDao
    abstract fun pendingMessageDao(): PendingMessageDao

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
    }
}
