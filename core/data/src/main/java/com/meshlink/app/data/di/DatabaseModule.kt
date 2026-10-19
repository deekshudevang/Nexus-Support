package com.meshlink.app.data.di

import android.content.Context
import androidx.room.Room
import com.meshlink.app.data.BuildConfig
import com.meshlink.app.data.local.AppDatabase
import com.meshlink.app.data.local.dao.DeviceDao
import com.meshlink.app.data.local.dao.MessageDao
import com.meshlink.app.data.local.dao.PendingMessageDao
import com.meshlink.app.data.security.SecureDbPassphraseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    init {
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            Timber.w(e, "Native sqlcipher library could not be loaded via System.loadLibrary (expected in Robolectric tests)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val passphraseBytes = SecureDbPassphraseProvider.getOrGeneratePassphrase(context)
        val hook = object : SQLiteDatabaseHook {
            override fun preKey(connection: SQLiteConnection) {
                // Pre-key hook runs before keying
            }

            override fun postKey(connection: SQLiteConnection) {
                // Post-key hook: transparently migrate legacy format databases to SQLCipher 4
                try {
                    connection.execute("PRAGMA cipher_migrate;", null, null)
                } catch (e: Exception) {
                    Timber.d("cipher_migrate check: ${e.message}")
                }
            }
        }
        val factory = SupportOpenHelperFactory(passphraseBytes, hook, false)
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
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14
            ).apply {
                if (BuildConfig.DEBUG) {
                    fallbackToDestructiveMigration()
                }
            }.build()
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
