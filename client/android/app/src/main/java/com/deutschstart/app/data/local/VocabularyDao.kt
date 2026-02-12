package com.deutschstart.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {
    @Query("SELECT * FROM vocabulary ORDER BY frequencyRank ASC")
    fun getAllVocabulary(): Flow<List<VocabularyEntity>>

    @Query("DELETE FROM vocabulary")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<VocabularyEntity>)

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    suspend fun getWordById(id: String): VocabularyEntity?

    // MVP SRS Query:
    // 1. New items (masteryLevel = 0)
    // 2. Due items (masteryLevel > 0 AND lastReviewedAt + interval < now) -> Logic complex for SQL
    // Simplified: Return items with low mastery or items not reviewed recently
    @Query("""
        SELECT * FROM vocabulary 
        WHERE masteryLevel = 0 
        OR (lastReviewedAt IS NOT NULL AND (:now - lastReviewedAt) > (masteryLevel * 60000)) 
        ORDER BY masteryLevel ASC, lastReviewedAt ASC 
        LIMIT :limit
    """)
    suspend fun getItemsForReview(now: Long, limit: Int): List<VocabularyEntity>

    @Query("UPDATE vocabulary SET masteryLevel = :mastery, lastReviewedAt = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: String, mastery: Int, timestamp: Long)

    @Query("SELECT COUNT(*) FROM vocabulary WHERE masteryLevel > 0")
    fun getLearnedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vocabulary")
    fun getTotalCount(): Flow<Int>

    // Due count (Simplified SRS logic repeated)
    @Query("""
        SELECT COUNT(*) FROM vocabulary 
        WHERE masteryLevel = 0 
        OR (lastReviewedAt IS NOT NULL AND (:now - lastReviewedAt) > (masteryLevel * 60000))
    """)
    fun getDueCount(now: Long): Flow<Int>
}
