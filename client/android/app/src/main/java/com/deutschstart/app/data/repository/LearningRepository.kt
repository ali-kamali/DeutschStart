package com.deutschstart.app.data.repository

import com.deutschstart.app.data.local.FsrsLogEntity
import com.deutschstart.app.data.local.VocabularyDao
import com.deutschstart.app.data.local.VocabularyEntity
import com.deutschstart.app.util.fsrs.FsrsAlgorithm
import com.deutschstart.app.util.fsrs.FsrsCard
import com.deutschstart.app.util.fsrs.Rating
import com.deutschstart.app.util.fsrs.State
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningRepository @Inject constructor(
    private val dao: VocabularyDao,
    private val fsrs: FsrsAlgorithm
) {
    suspend fun getDueItems(limit: Int = 10, offset: Int = 0): List<VocabularyEntity> {
        val now = System.currentTimeMillis()
        return dao.getItemsForReview(now, limit, offset)
    }

    suspend fun processResult(item: VocabularyEntity, rating: Int) {
        // Map UI rating (1..4) to FSRS Rating enum
        val fsrsRating = when (rating) {
            1 -> Rating.Again
            2 -> Rating.Hard
            3 -> Rating.Good
            4 -> Rating.Easy
            else -> Rating.Good
        }

        // Convert Entity -> FsrsCard
        val card = FsrsCard(
            due = item.scheduledDate?.let { Date(it) } ?: Date(),
            stability = item.stability,
            difficulty = item.difficulty,
            elapsedDays = item.elapsedDays,
            scheduledDays = item.scheduledDays,
            reps = item.reps,
            lapses = item.lapses,
            state = State.fromInt(item.state),
            lastReview = item.lastReviewedAt?.let { Date(it) }
        )

        val now = Date()
        val schedulingInfo = fsrs.schedule(card, now)[fsrsRating] ?: return

        // Convert Result -> Entity
        val newCard = schedulingInfo.card
        val newItem = item.copy(
            stability = newCard.stability,
            difficulty = newCard.difficulty,
            elapsedDays = newCard.elapsedDays,
            scheduledDays = newCard.scheduledDays,
            reps = newCard.reps,
            lapses = newCard.lapses,
            state = newCard.state.value,
            scheduledDate = newCard.due.time,
            lastReviewedAt = now.time
        )

        // Log the review
        val reviewLog = schedulingInfo.reviewLog
        val logEntity = FsrsLogEntity(
            cardId = item.id,
            rating = fsrsRating.value,
            scheduledDays = reviewLog.scheduledDays,
            elapsedDays = reviewLog.elapsedDays,
            reviewDuration = 0, // TODO: Track duration in UI
            state = reviewLog.state.value,
            reviewTime = now.time
        )

        dao.update(newItem)
        dao.insertLog(logEntity)

        // Leech detection: check AFTER FSRS update
        // Only check on "Again" (fail)
        if (fsrsRating == Rating.Again) {
            val updatedLapses = newItem.lapses
            val updatedReps = newItem.reps
            
            // Criteria: 5+ lapses OR >25% failure rate (ignored for new cards with < 5 reps)
            val isLeech = updatedLapses >= 5 ||
                (updatedReps > 4 && updatedLapses.toFloat() / updatedReps > 0.25f)
            
            if (isLeech && !newItem.isLeech) {
                // Auto-suspend and mark as leech
                dao.update(newItem.copy(isLeech = true, isSuspended = true))
            }
        }
    }

    suspend fun fixLeech(item: VocabularyEntity, newMnemonic: String?, newSentencesJson: String?) {
        val fixed = item.copy(
            isLeech = false,
            isSuspended = false,
            lapses = 0, // Reset lapses for fresh start
            genderMnemonic = newMnemonic ?: item.genderMnemonic,
            exampleSentencesJson = newSentencesJson ?: item.exampleSentencesJson
        )
        dao.update(fixed)
    }

    suspend fun suspendForever(item: VocabularyEntity) {
        // Remove from leech list but keep suspended
        dao.update(item.copy(isSuspended = true, isLeech = false))
    }

    suspend fun unsuspendCard(item: VocabularyEntity) {
        dao.update(item.copy(isSuspended = false, isLeech = false, lapses = 0))
    }

    fun predictIntervals(item: VocabularyEntity): Map<Int, Int> {
        val card = FsrsCard(
            due = item.scheduledDate?.let { Date(it) } ?: Date(),
            stability = item.stability,
            difficulty = item.difficulty,
            elapsedDays = item.elapsedDays,
            scheduledDays = item.scheduledDays,
            reps = item.reps,
            lapses = item.lapses,
            state = State.fromInt(item.state),
            lastReview = item.lastReviewedAt?.let { Date(it) }
        )
        
        val now = Date()
        val schedule = fsrs.schedule(card, now)
        
        return mapOf(
            1 to (schedule[Rating.Again]?.card?.scheduledDays ?: 0),
            2 to (schedule[Rating.Hard]?.card?.scheduledDays ?: 0),
            3 to (schedule[Rating.Good]?.card?.scheduledDays ?: 0),
            4 to (schedule[Rating.Easy]?.card?.scheduledDays ?: 0)
        )
    }
}
