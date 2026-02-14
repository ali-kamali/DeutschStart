package com.deutschstart.app.data.model

enum class XpReason(val xpAmount: Int) {
    SRS_REVIEW(1),        // +1 per card reviewed
    PERFECT_SESSION(5),   // +5 bonus for >90% accuracy in a session
    DAILY_GOAL_COMPLETE(10); // +10 bonus for reaching daily goal
}
