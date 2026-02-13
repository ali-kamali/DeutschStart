package com.deutschstart.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fsrs_logs",
    foreignKeys = [
        ForeignKey(
            entity = VocabularyEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cardId")]
)
data class FsrsLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val rating: Int, // 1=Again, 2=Hard, 3=Good, 4=Easy
    val scheduledDays: Int,
    val elapsedDays: Int,
    val reviewDuration: Long, // Time spent on card in ms
    val state: Int, // State BEFORE review
    val reviewTime: Long
)
