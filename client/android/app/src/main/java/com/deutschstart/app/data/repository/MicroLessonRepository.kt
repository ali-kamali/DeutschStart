package com.deutschstart.app.data.repository

import com.deutschstart.app.data.local.GrammarDao
import com.deutschstart.app.data.local.MicroLessonDao
import com.deutschstart.app.data.local.MicroLessonEntity
import com.deutschstart.app.data.local.VocabularyDao
import com.deutschstart.app.data.local.VocabularyEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MicroLessonRepository @Inject constructor(
    private val lessonDao: MicroLessonDao,
    private val vocabDao: VocabularyDao,
    private val grammarDao: GrammarDao,
    private val gamificationRepo: GamificationRepository
) {
    private val gson = Gson()
    
    // In-memory cache of current lesson state basics to drive UI quickly
    // Full state derived from DB + fetching entities
    
    suspend fun getOrCreateTodaysLesson(): MicroLessonEntity? {
        val existing = lessonDao.getToday()
        if (existing != null) {
            // Check if it's from today by comparing dates
            val lessonDay = java.util.Calendar.getInstance().apply { timeInMillis = existing.createdAt }
            val today = java.util.Calendar.getInstance()
            val isSameDay = lessonDay.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                    lessonDay.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
            
            if (isSameDay) return existing
            // Stale lesson from a previous day — create a new one
        }
        return startNewLesson()
    }

    suspend fun startNewLesson(): MicroLessonEntity? {
        // 1. Pick 5 items: Due items first, then New items
        // Explicitly fetch due items first to ensure priority
        val now = System.currentTimeMillis()
        val dueItems = vocabDao.getDueItems(now, 5)
        
        val targetVocab = if (dueItems.size < 5) {
            val needed = 5 - dueItems.size
            val newItems = vocabDao.getNewItems(needed)
            dueItems + newItems
        } else {
            dueItems
        }

        if (targetVocab.isEmpty()) {
            return null // Nothing to do
        }

        // 2. Pick Grammar Topic
        // Heuristic: Random from all topics for now (as per plan)
        // If null (no topics), we'll skip stage 1 later.
        val grammarTopic = grammarDao.getRandomTopic()

        // 3. Pick Playlist Words
        val playlistWords = vocabDao.getKnownWords(10)
        val playlistIds = playlistWords.map { it.id }

        val newLesson = MicroLessonEntity(
            stage = 0,
            vocabCompleted = 0,
            vocabWordIds = gson.toJson(targetVocab.map { it.id }),
            grammarTopicId = grammarTopic?.id,
            playlistWordIds = gson.toJson(playlistIds),
            createdAt = System.currentTimeMillis(),
            lastActive = System.currentTimeMillis()
        )

        lessonDao.insertOrUpdate(newLesson)
        return newLesson
    }

    suspend fun updateProgress(
        stage: Int, 
        vocabCompleted: Int, 
        grammarTopicId: String?, 
        playlistJson: String
    ) {
        val current = lessonDao.getToday() ?: return
        val updated = current.copy(
            stage = stage,
            vocabCompleted = vocabCompleted,
            grammarTopicId = grammarTopicId,
            playlistWordIds = playlistJson,
            lastActive = System.currentTimeMillis()
        )
        lessonDao.insertOrUpdate(updated)
    }

    suspend fun completeLesson() {
        // Mark as complete in UI (we don't persist "complete" state in Entity explicitly 
        // other than maybe deleting it or setting stage=3)
        // Let's set stage = 3 (Completed)
        val current = lessonDao.getToday() ?: return
        val updated = current.copy(stage = 3)
        lessonDao.insertOrUpdate(updated)
    }

    suspend fun getPlaylistWords(jsonIds: String): List<VocabularyEntity> {
        return getWordsByIds(jsonIds)
    }

    suspend fun getVocabWords(jsonIds: String): List<VocabularyEntity> {
        return getWordsByIds(jsonIds)
    }

    private suspend fun getWordsByIds(jsonIds: String): List<VocabularyEntity> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val ids: List<String> = gson.fromJson(jsonIds, type) ?: emptyList()
            ids.mapNotNull { vocabDao.getWordById(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
