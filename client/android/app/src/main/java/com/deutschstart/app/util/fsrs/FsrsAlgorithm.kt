package com.deutschstart.app.util.fsrs

import java.util.Calendar
import java.util.Date
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FsrsAlgorithm @Inject constructor() {
    private val p = FsrsParameters()

    fun schedule(card: FsrsCard, now: Date): Map<Rating, FsrsSchedulingInfo> {
        return mapOf(
            Rating.Again to process(card, Rating.Again, now),
            Rating.Hard to process(card, Rating.Hard, now),
            Rating.Good to process(card, Rating.Good, now),
            Rating.Easy to process(card, Rating.Easy, now)
        )
    }

    private fun process(card: FsrsCard, rating: Rating, now: Date): FsrsSchedulingInfo {
        // Calculate elapsed days
        val elapsedDays = if (card.lastReview != null) {
            ((now.time - card.lastReview.time) / (1000 * 60 * 60 * 24)).toInt()
        } else {
            0
        }

        val reps = card.reps + 1
        val lapses = if (rating == Rating.Again) card.lapses + 1 else card.lapses

        var stability: Double
        var difficulty: Double
        var scheduledDays: Int
        var state: State

        when (card.state) {
            State.New -> {
                // Initial stability and difficulty
                stability = p.w[rating.value - 1]
                difficulty = constrainDifficulty(p.w[4] - p.w[5] * (rating.value - 3))
                state = if (rating == Rating.Again) State.Learning else State.Review
                scheduledDays = 0
            }
            State.Learning, State.Relearning -> {
                stability = card.stability
                difficulty = card.difficulty
                if (rating == Rating.Good || rating == Rating.Easy) {
                    state = State.Review
                    scheduledDays = nextInterval(stability)
                } else {
                    state = State.Learning
                    scheduledDays = 0
                }
            }
            State.Review -> {
                val lastD = card.difficulty
                val lastS = card.stability
                val retrievability = forgettingCurve(elapsedDays, lastS)

                // Next Difficulty: D' = D - w6 * (rating - 3), then mean reversion
                var nextDiff = lastD - p.w[6] * (rating.value - 3)
                nextDiff = constrainDifficulty(nextDiff)
                nextDiff += p.w[7] * (p.w[4] - nextDiff)
                difficulty = constrainDifficulty(nextDiff)

                // Next Stability
                if (rating == Rating.Again) {
                    state = State.Relearning
                    scheduledDays = 0
                    stability = nextForgetStability(lastD, lastS, retrievability)
                } else {
                    state = State.Review
                    stability = nextRecallStability(lastD, lastS, retrievability, rating)
                    scheduledDays = nextInterval(stability)
                }
            }
        }

        // Ensure scheduledDays >= 1 for Review state
        if (state == State.Review && scheduledDays < 1) {
            scheduledDays = 1
        }

        // Calculate next due date
        val calendar = Calendar.getInstance()
        calendar.time = now
        if (scheduledDays > 0) {
            calendar.add(Calendar.DAY_OF_YEAR, scheduledDays)
        }
        val due = calendar.time

        val newCard = card.copy(
            due = due,
            stability = stability,
            difficulty = difficulty,
            elapsedDays = elapsedDays,
            scheduledDays = scheduledDays,
            reps = reps,
            lapses = lapses,
            state = state,
            lastReview = now
        )

        return FsrsSchedulingInfo(
            card = newCard,
            reviewLog = FsrsReviewLog(
                rating = rating,
                scheduledDays = scheduledDays,
                elapsedDays = elapsedDays,
                review = now,
                state = state
            )
        )
    }

    private fun nextRecallStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.Hard) p.w[15] else 1.0
        val easyBonus = if (rating == Rating.Easy) p.w[16] else 1.0

        return s * (1 + exp(p.w[8]) *
            (11 - d) *
            s.pow(-p.w[9]) *
            (exp(p.w[10] * (1 - r)) - 1) *
            hardPenalty *
            easyBonus)
    }

    private fun nextForgetStability(d: Double, s: Double, r: Double): Double {
        return min(
            p.w[11] * d.pow(-p.w[12]) * ((s + 1).pow(p.w[13]) - 1) * exp(p.w[14] * (1 - r)),
            s
        )
    }

    private fun forgettingCurve(elapsedDays: Int, stability: Double): Double {
        return (1 + elapsedDays / (9 * stability)).pow(-1)
    }

    private fun nextInterval(stability: Double): Int {
        val newInterval = stability * 9 * (1 / p.requestRetention - 1)
        return max(1, min(newInterval.roundToInt(), p.maximumInterval))
    }

    private fun constrainDifficulty(d: Double): Double {
        return min(max(d, 1.0), 10.0)
    }
}
