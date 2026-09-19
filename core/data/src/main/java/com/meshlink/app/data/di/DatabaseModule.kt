package com.meshlink.app.data.di

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Room
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
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val passphrase = getOrCreateDatabasePassphrase()
        val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase))
        return Room.databaseBuilder(context, AppDatabase::class.java, "meshlink.db")
            .openHelperFactory(factory)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12
            ).fallbackToDestructiveMigration()
            .build()
    }

    /**
     * Returns (or creates) a 256-bit AES key stored in AndroidKeyStore,
     * then exports it as a char array to use as the SQLCipher passphrase.
     * The key never leaves the hardware-backed KeyStore as plaintext.
     */
    private fun getOrCreateDatabasePassphrase(): CharArray {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "meshlink_db_key"
        if (!ks.containsAlias(alias)) {
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGen.init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGen.generateKey()
        }
        val secretKey = ks.getKey(alias, null) as SecretKey
        // Use hex of encoded key bytes as the passphrase (256-bit key → 64 hex chars)
        return secretKey.encoded.joinToString("") { "%02x".format(it) }.toCharArray()
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
}
