package com.deutschstart.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [VocabularyEntity::class, FsrsLogEntity::class, GrammarTopicEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun grammarDao(): GrammarDao
}
