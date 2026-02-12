package com.deutschstart.app.data.repository

import com.deutschstart.app.data.local.VocabularyDao
import com.deutschstart.app.data.local.VocabularyEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningRepository @Inject constructor(
    private val dao: VocabularyDao
) {
    suspend fun getDueItems(limit: Int = 10): List<VocabularyEntity> {
        val now = System.currentTimeMillis()
        return dao.getItemsForReview(now, limit)
    }

    suspend fun processResult(item: VocabularyEntity, quality: Int) {
        // Quality: 1 (Again), 2 (Hard), 3 (Good), 4 (Easy)
        val newMastery = when (quality) {
            1 -> 0                                          // Again: reset
            2 -> maxOf(item.masteryLevel - 1, 0)            // Hard: demote one level
            3 -> item.masteryLevel + 1                      // Good: promote one level
            4 -> item.masteryLevel + 2                      // Easy: skip a level
            else -> item.masteryLevel
        }

        dao.updateProgress(item.id, newMastery, System.currentTimeMillis())
    }
}
