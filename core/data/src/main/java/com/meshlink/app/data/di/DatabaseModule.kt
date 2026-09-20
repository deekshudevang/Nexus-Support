package com.meshlink.app.data.di

import android.content.Context
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.meshlink.app.data.local.AppDatabase
import com.meshlink.app.data.local.dao.DeviceDao
import com.meshlink.app.data.local.dao.MessageDao
import com.meshlink.app.data.local.dao.PendingMessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val dbAuthToken = getOrCreateDatabaseAuthToken(context)
        val factory = SupportFactory(SQLiteDatabase.getBytes(dbAuthToken))
        return Room.databaseBuilder(context, AppDatabase::class.java, "meshlink.db")
            .openHelperFactory(factory)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13
            ).fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    /**
     * Generates a random 256-bit token on first run and stores it securely using
     * EncryptedSharedPreferences (backed by AndroidKeyStore AES-256-GCM MasterKey).
     */
    private fun getOrCreateDatabaseAuthToken(context: Context): CharArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPrefs = EncryptedSharedPreferences.create(
            context,
            "meshlink_db_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val PREF_KEY_DB_PASSPHRASE = "db_passphrase" // lgtm [kt/hardcoded-credentials]
        var dbAuthToken = sharedPrefs.getString(PREF_KEY_DB_PASSPHRASE, null)
        if (dbAuthToken == null) {
            val bytes = ByteArray(32)
            java.security.SecureRandom().nextBytes(bytes)
            dbAuthToken = bytes.joinToString("") { "%02x".format(it) }
            sharedPrefs.edit().putString(PREF_KEY_DB_PASSPHRASE, dbAuthToken).apply()
        }
        return dbAuthToken.toCharArray()
    }

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()

    @Provides
    fun providePendingMessageDao(appDatabase: AppDatabase): PendingMessageDao = appDatabase.pendingMessageDao()

    @Provides
    fun provideLocationEventDao(appDatabase: AppDatabase): com.meshlink.app.data.local.dao.LocationEventDao = appDatabase.locationEventDao()

    @Provides
    fun provideLocationSyncQueueDao(appDatabase: AppDatabase): com.meshlink.app.data.local.dao.LocationSyncQueueDao = appDatabase.locationSyncQueueDao()

    @Provides
    fun provideProcessedEventDao(appDatabase: AppDatabase): com.meshlink.app.data.local.dao.ProcessedEventDao = appDatabase.processedEventDao()

    @Provides
    fun provideSosPacketDao(appDatabase: AppDatabase): com.meshlink.app.data.local.dao.SosPacketDao = appDatabase.sosPacketDao()
}
