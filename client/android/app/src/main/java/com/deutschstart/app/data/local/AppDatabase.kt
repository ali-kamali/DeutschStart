package com.deutschstart.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [VocabularyEntity::class, FsrsLogEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
}
