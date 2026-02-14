package com.deutschstart.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [VocabularyEntity::class, FsrsLogEntity::class, GrammarTopicEntity::class, UserProgressEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun grammarDao(): GrammarDao
    abstract fun userProgressDao(): UserProgressDao
}
