package com.deutschstart.app.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschstart.app.data.repository.LearningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val isLoading: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val currentCardIndex: Int = 0,
    val totalCards: Int = 0,
    val progress: Float = 0f,
    val config: PlaylistConfig = PlaylistConfig(),
    val error: String? = null,
    val currentCard: com.deutschstart.app.data.local.VocabularyEntity? = null,
    val currentSentence: String? = null,
    val currentSentenceEn: String? = null,
    val kaikkiInfo: KaikkiInfo? = null,
    val germanSpeed: Float = 1.0f,
    val englishSpeed: Float = 1.0f,
    val loopEnabled: Boolean = false
)

data class KaikkiInfo(val ipa: String?, val senses: List<String>)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: LearningRepository,
    private val playlistPlayer: PlaylistPlayer,
    private val playlistBuilder: PlaylistBuilder
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PlaylistUiState(isLoading = true))
    val uiState: StateFlow<PlaylistUiState> = _uiState
    
    private var currentPlaylistData: PlaylistData? = null
    private var cards: List<com.deutschstart.app.data.local.VocabularyEntity> = emptyList()
    private var currentOffset: Int = 0
    
    init {
        // Observe player state
        viewModelScope.launch {
            combine(
                playlistPlayer.playbackState,
                playlistPlayer.currentCardIndex,
                playlistPlayer.progress,
                playlistPlayer.germanSpeed,
                playlistPlayer.englishSpeed
            ) { playbackState, cardIndex, progress, deSpeed, enSpeed ->
                data class PlayerState(
                    val playbackState: PlaybackState,
                    val cardIndex: Int,
                    val progress: Float,
                    val deSpeed: Float,
                    val enSpeed: Float
                )
                PlayerState(playbackState, cardIndex, progress, deSpeed, enSpeed)
            }.collect { (playbackState, cardIndex, progress, deSpeed, enSpeed) ->
                var currentCard = _uiState.value.currentCard
                var currentSentence = _uiState.value.currentSentence
                var currentSentenceEn = _uiState.value.currentSentenceEn
                var kaikkiInfo = _uiState.value.kaikkiInfo
                
                // Check if we need to update card info
                // Update if index changed OR if we have cards but no current card shown (e.g. after initial load)
                val needUpdate = (cardIndex != _uiState.value.currentCardIndex) || 
                                 (currentCard == null && cards.isNotEmpty() && cardIndex >= 0 && cardIndex < cards.size)
                
                if (needUpdate) {
                    if (cardIndex >= 0 && cardIndex < cards.size) {
                        val card = cards[cardIndex]
                        currentCard = card
                        val sentenceData = extractFirstSentenceData(card.exampleSentencesJson)
                        currentSentence = sentenceData?.first
                        currentSentenceEn = sentenceData?.second
                        kaikkiInfo = extractKaikkiInfo(card.kaikkiDataJson)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    playbackState = playbackState,
                    currentCardIndex = cardIndex,
                    progress = progress,
                    germanSpeed = deSpeed,
                    englishSpeed = enSpeed,
                    isLoading = false,
                    currentCard = currentCard,
                    currentSentence = currentSentence,
                    currentSentenceEn = currentSentenceEn,
                    kaikkiInfo = kaikkiInfo
                )
            }
        }
        // Observe loop state separately
        viewModelScope.launch {
            playlistPlayer.loopEnabled.collect { looping ->
                _uiState.value = _uiState.value.copy(loopEnabled = looping)
            }
        }
    }
    
    // Helper to extract first sentence data (German, English) from JSON
    private fun extractFirstSentenceData(json: String): Pair<String?, String?>? {
        try {
            if (json.isBlank()) return null
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
            val sentences: List<Map<String, String>> = com.google.gson.Gson().fromJson(json, type)
            val first = sentences.firstOrNull()
            return Pair(first?.get("german"), first?.get("english"))
        } catch (e: Exception) {
            return null
        }
    }

    // Helper to extract Kaikki data
    fun extractKaikkiInfo(json: String?): KaikkiInfo? {
        if (json.isNullOrBlank()) return null
        try {
            // Kaikki data structure: map (senses: list, sounds: list)
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = com.google.gson.Gson().fromJson(json, type)
            
            // Extract IPA
            var ipa: String? = null
            val sounds = data["sounds"] as? List<Map<String, Any>>
            if (sounds != null) {
                ipa = sounds.firstOrNull { it.containsKey("ipa") }?.get("ipa") as? String
            }
            
            // Extract Senses
            val sensesList = mutableListOf<String>()
            val senses = data["senses"] as? List<Map<String, Any>>
            if (senses != null) {
                senses.take(3).forEach { sense ->
                    val glosses = sense["glosses"] as? List<String>
                    if (glosses != null && glosses.isNotEmpty()) {
                        sensesList.add(glosses.joinToString(", "))
                    }
                }
            }
            
            return KaikkiInfo(ipa, sensesList)
        } catch (e: Exception) {
            return null
        }
    }
    
    fun playKaikkiAudio() {
        // Manually play the kaikki audio of current card
        _uiState.value.currentCard?.kaikkiAudioPath?.let { path ->
            playlistPlayer.playSingleAudio(path)
        }
    }

    fun loadPlaylist(limit: Int = 20, config: PlaylistConfig = PlaylistConfig()) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, config = config)
            
            try {
                // Get due items from repository
                val fethcedCards = repository.getDueItems(limit)
                
                if (fethcedCards.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No cards due for review"
                    )
                    return@launch
                }
                
                cards = fethcedCards
                buildAndLoadPlaylist()
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load playlist"
                )
            }
        }
    }

    private fun buildAndLoadPlaylist() {
        if (cards.isEmpty()) return

        try {
            val config = _uiState.value.config
            // Build playlist
            val playlistData = playlistBuilder.buildPlaylist(cards, config) 
            currentPlaylistData = playlistData

            // Load into player
            playlistPlayer.loadPlaylist(playlistData)
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                totalCards = cards.size,
                error = null
            )
        } catch (e: Exception) {
             _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = e.message ?: "Failed to build playlist"
            )
        }
    }
    
    fun play() {
        playlistPlayer.play()
    }
    
    fun pause() {
        playlistPlayer.pause()
    }
    
    fun skipForward() {
        playlistPlayer.skipForward()
    }
    
    fun skipBackward() {
        playlistPlayer.skipBackward()
    }
    
    fun updateConfig(config: PlaylistConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(config = config)
            buildAndLoadPlaylist()
        }
    }

    fun setGermanSpeed(speed: Float) {
        playlistPlayer.setGermanSpeed(speed)
    }
    
    fun setEnglishSpeed(speed: Float) {
        playlistPlayer.setEnglishSpeed(speed)
    }
    
    fun toggleSentences(enabled: Boolean) {
        val newConfig = _uiState.value.config.copy(includeSentences = enabled)
        // "Vocab Only Loop" toggle: when sentences are disabled, loop is enabled
        playlistPlayer.setLoopEnabled(!enabled)
        updateConfig(newConfig)
    }

    fun loadNextBatch() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                currentOffset += cards.size
                val nextCards = repository.getDueItems(limit = 20, offset = currentOffset)
                
                if (nextCards.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No more cards available"
                    )
                    return@launch
                }
                
                cards = nextCards
                buildAndLoadPlaylist()
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load next batch"
                )
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        playlistPlayer.release()
    }
}
