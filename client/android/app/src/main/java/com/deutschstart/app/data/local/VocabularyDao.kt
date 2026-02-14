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

    @Query("SELECT * FROM vocabulary WHERE isLeech = 1 ORDER BY lapses DESC")
    fun getLeeches(): Flow<List<VocabularyEntity>>

    @Query("SELECT COUNT(*) FROM vocabulary WHERE isLeech = 1")
    fun getLeechCount(): Flow<Int>

    // FSRS Query:
    // 1. New cards (state = 0), limit to a daily new count (e.g. 10) - logic handled in Repo usually, 
    //    but here we just fetch mixed.
    // 2. Due cards (due <= now)
    // Order: Reviews first (critical), then New
    // Exclude Suspended/Leech cards
    @Query("""
        SELECT * FROM vocabulary 
        WHERE 
            isSuspended = 0 AND (
            (state = 0) OR 
            (scheduledDate IS NOT NULL AND scheduledDate <= :now)
            )
        ORDER BY 
            CASE WHEN state = 2 THEN 0 ELSE 1 END,
            scheduledDate ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getItemsForReview(now: Long, limit: Int, offset: Int = 0): List<VocabularyEntity>

    // Count learned (State > 0)
    @Query("SELECT COUNT(*) FROM vocabulary WHERE state > 0")
    fun getLearnedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vocabulary")
    fun getTotalCount(): Flow<Int>

    // Due count (Due <= Now), excluding suspended
    @Query("SELECT COUNT(*) FROM vocabulary WHERE isSuspended = 0 AND scheduledDate IS NOT NULL AND scheduledDate <= :now")
    fun getDueCount(now: Long): Flow<Int>
    @Query("SELECT id, word, gender, isLeech, isSuspended, lapses FROM vocabulary")
    suspend fun getAllSimple(): List<VocabularySimpleItem>
}

data class VocabularySimpleItem(
    val id: String,
    val word: String,
    val gender: String?,
    val isLeech: Boolean,
    val isSuspended: Boolean,
    val lapses: Int
)
