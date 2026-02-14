package com.deutschstart.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "grammar_topics")
data class GrammarTopicEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String?,
    val sequenceOrder: Int,
    val contentJson: String, // Store as JSON string
    val exercisesJson: String // Store as JSON string
)
