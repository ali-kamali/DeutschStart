package com.deutschstart.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "micro_lessons")
data class MicroLessonEntity(
    @PrimaryKey val id: String = "today",  // Single active lesson per day
    val stage: Int = 0,                    // 0=vocab, 1=grammar, 2=playlist, 3=complete
    val vocabCompleted: Int = 0,           // 0-5 SRS cards
    val vocabWordIds: String = "[]",       // JSON array of selected SRS word IDs
    val grammarTopicId: String? = null,    // e.g. "dative_case"
    val playlistWordIds: String = "[]",    // JSON array of 10 word IDs
    val createdAt: Long = 0,
    val lastActive: Long = 0
)
