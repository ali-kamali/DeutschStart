package com.deutschstart.app.ui.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschstart.app.data.local.VocabularyEntity
import com.deutschstart.app.data.repository.LearningRepository
import com.deutschstart.app.data.model.XpReason
import com.deutschstart.app.data.repository.GamificationRepository
import com.deutschstart.app.util.audio.AudioController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudySessionState(
    val currentCard: VocabularyEntity? = null,
    val isFlipped: Boolean = false,
    val isLoading: Boolean = false,
    val isSessionComplete: Boolean = false,
    val sentences: List<Map<String, String>> = emptyList(),
    val cardsCompleted: Int = 0,
    val totalSessionSize: Int = 0,
    val intervals: Map<Int, Int> = emptyMap(), // key: rating(1-4), value: days
    val sessionXp: Int = 0,
    val sessionAccuracy: Float = 0f
) {
    val progress: Float
        get() = if (totalSessionSize > 0) cardsCompleted.toFloat() / totalSessionSize else 0f
}

@HiltViewModel
class FlashcardViewModel @Inject constructor(
    private val repository: LearningRepository,
    private val gamificationRepo: GamificationRepository,
    private val audioController: AudioController
) : ViewModel() {

    private val _state = MutableStateFlow(StudySessionState(isLoading = true))
    val state: StateFlow<StudySessionState> = _state

    private val gson = Gson()
    private var sessionQueue: MutableList<VocabularyEntity> = mutableListOf()
    private var totalSize = 0
    private var correctCount = 0
    private var earnedXp = 0

    init {
        loadSession()
    }

    fun loadSession() {
        viewModelScope.launch {
            _state.value = StudySessionState(isLoading = true)
            val items = repository.getDueItems(10)
            sessionQueue = items.toMutableList()
            totalSize = items.size
            correctCount = 0
            earnedXp = 0
            
            // Preload audio for all cards in session
            val audioPaths = items.mapNotNull { it.audioLearnPath.takeIf { path -> path.isNotBlank() } }
            audioController.preloadVocabularyAudio(audioPaths)
            
            loadNextCard()
        }
    }

    private fun loadNextCard() {
        val completed = totalSize - sessionQueue.size
        if (sessionQueue.isEmpty()) {
            finishSession(completed)
        } else {
            val next = sessionQueue.removeAt(0)
            val sentences = parseSentences(next.exampleSentencesJson)
            _state.value = StudySessionState(
                currentCard = next,
                isFlipped = false,
                isLoading = false,
                sentences = sentences,
                cardsCompleted = completed,
                totalSessionSize = totalSize
            )
        }
    }
    
    private fun finishSession(completed: Int) {
        viewModelScope.launch {
            // Check for Perfect Session Bonus
            val accuracy = if (totalSize > 0) correctCount.toFloat() / totalSize else 0f
            if (accuracy >= 0.9f && totalSize >= 5) { // Min 5 cards for bonus
                gamificationRepo.awardXp(XpReason.PERFECT_SESSION.xpAmount, XpReason.PERFECT_SESSION)
                earnedXp += XpReason.PERFECT_SESSION.xpAmount
            }
            
            _state.value = _state.value.copy(
                currentCard = null,
                isLoading = false,
                isSessionComplete = true,
                cardsCompleted = completed,
                totalSessionSize = totalSize,
                sessionXp = earnedXp,
                sessionAccuracy = accuracy
            )
        }
    }

    fun flipCard() {
        val card = _state.value.currentCard
        val intervals = if (card != null) {
            repository.predictIntervals(card)
        } else {
            emptyMap()
        }
        
        _state.value = _state.value.copy(
            isFlipped = true,
            intervals = intervals
        )
        playAudio()
    }

    fun playAudio() {
        val card = _state.value.currentCard ?: return
        audioController.playVocabularyAudio(card.audioLearnPath)
    }

    fun playSentenceAudio(path: String) {
        audioController.playSentenceAudio(path)
    }

    fun rateCard(quality: Int) {
        val card = _state.value.currentCard ?: return
        viewModelScope.launch {
            repository.processResult(card, quality)
            
            // Award XP
            gamificationRepo.awardXp(XpReason.SRS_REVIEW.xpAmount, XpReason.SRS_REVIEW)
            earnedXp += XpReason.SRS_REVIEW.xpAmount
            
            // Track accuracy (3=Good, 4=Easy counts as correct)
            if (quality >= 3) {
                correctCount++
            }
            
            loadNextCard()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioController.release()
    }

    private fun parseSentences(json: String): List<Map<String, String>> {
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<Map<String, String>>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
