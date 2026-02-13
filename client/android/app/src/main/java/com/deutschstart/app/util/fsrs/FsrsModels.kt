package com.deutschstart.app.util.fsrs

import java.util.Date

/**
 * State of the card in the FSRS workflow.
 */
enum class State(val value: Int) {
    New(0),
    Learning(1),
    Review(2),
    Relearning(3);

    companion object {
        fun fromInt(value: Int) = entries.first { it.value == value }
    }
}

/**
 * Rating given by the user during review.
 */
enum class Rating(val value: Int) {
    Manual(0),
    Again(1),
    Hard(2),
    Good(3),
    Easy(4);
}

/**
 * Represents a Card's state for FSRS calculations.
 */
data class FsrsCard(
    val due: Date = Date(),
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val elapsedDays: Int = 0,
    val scheduledDays: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val state: State = State.New,
    val lastReview: Date? = null
)

/**
 * Log entry for a review event.
 */
data class FsrsReviewLog(
    val rating: Rating,
    val scheduledDays: Int,
    val elapsedDays: Int,
    val review: Date,
    val state: State
)

/**
 * Result of the scheduling algorithm.
 */
data class FsrsSchedulingInfo(
    val card: FsrsCard,
    val reviewLog: FsrsReviewLog
)

/**
 * Parameters for the FSRS v4 algorithm.
 * Default weights from the generic preset.
 */
data class FsrsParameters(
    val requestRetention: Double = 0.9,
    val maximumInterval: Int = 36500,
    val w: DoubleArray = doubleArrayOf(
        0.4, 0.6, 2.4, 5.8, 4.93, 0.94, 0.86, 0.01, 1.49, 0.14, 0.94, 2.18, 0.05, 0.34, 1.26, 0.29, 2.61
    )
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FsrsParameters

        if (requestRetention != other.requestRetention) return false
        if (maximumInterval != other.maximumInterval) return false
        if (!w.contentEquals(other.w)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requestRetention.hashCode()
        result = 31 * result + maximumInterval
        result = 31 * result + w.contentHashCode()
        return result
    }
}
