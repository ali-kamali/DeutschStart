package com.deutschstart.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vocabulary")
data class VocabularyEntity(
    @PrimaryKey val id: String,
    val word: String,
    val article: String?,
    val gender: String?,
    val pluralForm: String?,
    val ipa: String?,
    val verbPrefix: String?,
    val verbStem: String?,
    val partOfSpeech: String,
    val audioLearnPath: String,
    val audioReviewPath: String,
    val audioEnPath: String = "",  // English translation audio for Smart Playlist
    val translationEn: String,
    val exampleSentencesJson: String,
    val frequencyRank: Int,
    val category: String,
    val genderMnemonic: String?,
    // FSRS v4 fields
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val elapsedDays: Int = 0,
    val scheduledDays: Int = 0,
    val state: Int = 0, 
    val scheduledDate: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    // Legacy/Auxiliary
    val lastReviewedAt: Long? = null
)
