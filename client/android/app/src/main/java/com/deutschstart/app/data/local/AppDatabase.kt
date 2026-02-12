package com.deutschstart.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [VocabularyEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
}
