package com.deutschstart.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MicroLessonDao {
    @Query("SELECT * FROM micro_lessons WHERE id = 'today'")
    suspend fun getToday(): MicroLessonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(lesson: MicroLessonEntity)

    // Optional: cleanup old lessons if we ever change ID strategy, 
    // but with id='today' fixed, we just overwrite. 
    // Keeping this for future-proofing or if we switch to date-based IDs.
    @Query("DELETE FROM micro_lessons WHERE id != 'today'")
    suspend fun deleteOld()
}
