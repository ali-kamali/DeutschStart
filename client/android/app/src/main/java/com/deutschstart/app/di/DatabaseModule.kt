package com.deutschstart.app.di

import android.content.Context
import androidx.room.Room
import com.deutschstart.app.data.local.AppDatabase
import com.deutschstart.app.data.local.VocabularyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        // Load native libraries for SQLCipher
        System.loadLibrary("sqlcipher")
        
        // TODO: Move this to a secure Keystore implementation for production
        val passphrase = "deutschstart-secret-key-change-me".toByteArray()
        val factory = SupportOpenHelperFactory(passphrase)
        
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "deutschstart.db"
        )
        .openHelperFactory(factory)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideVocabularyDao(db: AppDatabase): VocabularyDao = db.vocabularyDao()
}
