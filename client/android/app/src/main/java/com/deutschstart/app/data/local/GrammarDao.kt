package com.deutschstart.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GrammarDao {
    @Query("SELECT * FROM grammar_topics ORDER BY sequenceOrder ASC")
    fun getAllTopics(): Flow<List<GrammarTopicEntity>>

    @Query("SELECT * FROM grammar_topics WHERE id = :topicId")
    suspend fun getTopicById(topicId: String): GrammarTopicEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(topics: List<GrammarTopicEntity>)

    @Query("DELETE FROM grammar_topics")
    suspend fun deleteAll()

    @Query("SELECT * FROM grammar_topics ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomTopic(): GrammarTopicEntity?
}
