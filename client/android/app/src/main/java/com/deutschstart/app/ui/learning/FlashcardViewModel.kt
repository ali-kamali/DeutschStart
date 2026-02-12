package com.deutschstart.app.ui.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschstart.app.data.local.VocabularyEntity
import com.deutschstart.app.data.repository.LearningRepository
import com.deutschstart.app.util.AudioPlayer
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
    val totalSessionSize: Int = 0
) {
    val progress: Float
        get() = if (totalSessionSize > 0) cardsCompleted.toFloat() / totalSessionSize else 0f
}

@HiltViewModel
class FlashcardViewModel @Inject constructor(
    private val repository: LearningRepository,
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    private val _state = MutableStateFlow(StudySessionState(isLoading = true))
    val state: StateFlow<StudySessionState> = _state

    private val gson = Gson()
    private var sessionQueue: MutableList<VocabularyEntity> = mutableListOf()
    private var totalSize = 0

    init {
        loadSession()
    }

    fun loadSession() {
        viewModelScope.launch {
            _state.value = StudySessionState(isLoading = true)
            val items = repository.getDueItems(10)
            sessionQueue = items.toMutableList()
            totalSize = items.size
            loadNextCard()
        }
    }

    private fun loadNextCard() {
        val completed = totalSize - sessionQueue.size
        if (sessionQueue.isEmpty()) {
            _state.value = _state.value.copy(
                currentCard = null,
                isLoading = false,
                isSessionComplete = true,
                cardsCompleted = completed,
                totalSessionSize = totalSize
            )
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

    fun flipCard() {
        _state.value = _state.value.copy(isFlipped = true)
        playAudio()
    }

    fun playAudio() {
        val card = _state.value.currentCard ?: return
        viewModelScope.launch {
            audioPlayer.playFile(card.audioLearnPath)
        }
    }

    fun playSentenceAudio(path: String) {
        viewModelScope.launch {
            audioPlayer.playFile(path)
        }
    }

    fun rateCard(quality: Int) {
        val card = _state.value.currentCard ?: return
        viewModelScope.launch {
            repository.processResult(card, quality)
            loadNextCard()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
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
