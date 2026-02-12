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
    val translationEn: String,
    val exampleSentencesJson: String, // Stored as JSON string
    val frequencyRank: Int,
    val category: String,
    val genderMnemonic: String?,
    val masteryLevel: Int = 0,
    val lastReviewedAt: Long? = null
)
