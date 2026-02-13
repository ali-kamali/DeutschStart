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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: FsrsLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: VocabularyEntity)

    // FSRS Query:
    // 1. New cards (state = 0), limit to a daily new count (e.g. 10) - logic handled in Repo usually, 
    //    but here we just fetch mixed.
    // 2. Due cards (due <= now)
    // Order: Reviews first (critical), then New
    @Query("""
        SELECT * FROM vocabulary 
        WHERE 
            (state = 0) OR 
            (scheduledDate IS NOT NULL AND scheduledDate <= :now)
        ORDER BY 
            CASE WHEN state = 2 THEN 0 ELSE 1 END,
            scheduledDate ASC
        LIMIT :limit
    """)
    suspend fun getItemsForReview(now: Long, limit: Int): List<VocabularyEntity>

    // Count learned (State > 0)
    @Query("SELECT COUNT(*) FROM vocabulary WHERE state > 0")
    fun getLearnedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vocabulary")
    fun getTotalCount(): Flow<Int>

    // Due count (Due <= Now)
    @Query("SELECT COUNT(*) FROM vocabulary WHERE scheduledDate IS NOT NULL AND scheduledDate <= :now")
    fun getDueCount(now: Long): Flow<Int>
}
