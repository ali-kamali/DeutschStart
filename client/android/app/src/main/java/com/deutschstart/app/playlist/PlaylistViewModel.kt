package com.deutschstart.app.playlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschstart.app.data.repository.LearningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val isLoading: Boolean = true,
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
    val loopEnabled: Boolean = false,
    val comprehension: Float = 0f
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
        Log.d("PlaylistVM", "init: ViewModel created, starting observers and loading")
        
        // Observe player playback state
        viewModelScope.launch {
            playlistPlayer.playbackState.collect { playbackState ->
                _uiState.update { it.copy(playbackState = playbackState) }
            }
        }
        
        // Observe player card index
        viewModelScope.launch {
            playlistPlayer.currentCardIndex.collect { cardIndex ->
                updateCardInfo(cardIndex)
            }
        }
        
        // Observe player progress
        viewModelScope.launch {
            playlistPlayer.progress.collect { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
        }
        
        // Observe speeds
        viewModelScope.launch {
            playlistPlayer.germanSpeed.collect { speed ->
                _uiState.update { it.copy(germanSpeed = speed) }
            }
        }
        viewModelScope.launch {
            playlistPlayer.englishSpeed.collect { speed ->
                _uiState.update { it.copy(englishSpeed = speed) }
            }
        }
        
        // Observe loop state
        viewModelScope.launch {
            playlistPlayer.loopEnabled.collect { looping ->
                _uiState.update { it.copy(loopEnabled = looping) }
            }
        }
        
        // Auto-load initial playlist
        loadPlaylist(limit = 20)
    }
    
    private fun updateCardInfo(cardIndex: Int) {
        if (cardIndex >= 0 && cardIndex < cards.size) {
            val card = cards[cardIndex]
            val sentenceData = extractFirstSentenceData(card.exampleSentencesJson)
            val kaikkiInfo = extractKaikkiInfo(card.kaikkiDataJson)
            
            _uiState.update {
                it.copy(
                    currentCardIndex = cardIndex,
                    currentCard = card,
                    currentSentence = sentenceData?.first,
                    currentSentenceEn = sentenceData?.second,
                    kaikkiInfo = kaikkiInfo
                )
            }
        } else {
            _uiState.update { it.copy(currentCardIndex = cardIndex) }
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
            Log.d("PlaylistVM", "loadPlaylist: Starting, limit=$limit")
            _uiState.update { it.copy(isLoading = true, config = config, error = null) }
            
            try {
                // Get due items from repository
                val fetchedCards = repository.getDueItems(limit)
                Log.d("PlaylistVM", "loadPlaylist: Fetched ${fetchedCards.size} cards")
                
                if (fetchedCards.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, error = "No cards due for review") }
                    return@launch
                }
                
                cards = fetchedCards
                
                // Calculate batch comprehension
                val knownCount = cards.count { it.state > 0 } // State > 0 means learned/known
                val comp = if (cards.isNotEmpty()) knownCount.toFloat() / cards.size else 0f
                
                _uiState.update { it.copy(comprehension = comp) }

                buildAndLoadPlaylist()
                Log.d("PlaylistVM", "loadPlaylist: Complete, totalCards=${cards.size}")
                
            } catch (e: Exception) {
                Log.e("PlaylistVM", "loadPlaylist: Failed", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load playlist") }
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
            Log.d("PlaylistVM", "buildAndLoadPlaylist: Built ${playlistData.segments.size} segments")

            // Load into player
            playlistPlayer.loadPlaylist(playlistData)
            
            // Update first card info immediately
            if (cards.isNotEmpty()) {
                val firstCard = cards[0]
                val sentenceData = extractFirstSentenceData(firstCard.exampleSentencesJson)
                val kaikkiInfo = extractKaikkiInfo(firstCard.kaikkiDataJson)
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totalCards = cards.size,
                        error = null,
                        currentCard = firstCard,
                        currentCardIndex = 0,
                        currentSentence = sentenceData?.first,
                        currentSentenceEn = sentenceData?.second,
                        kaikkiInfo = kaikkiInfo
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, totalCards = cards.size, error = null) }
            }
            
            Log.d("PlaylistVM", "buildAndLoadPlaylist: Done, isLoading=false")
        } catch (e: Exception) {
            Log.e("PlaylistVM", "buildAndLoadPlaylist: Failed", e)
            _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to build playlist") }
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
            _uiState.update { it.copy(config = config) }
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
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                currentOffset += cards.size
                val nextCards = repository.getDueItems(limit = 20, offset = currentOffset)
                
                if (nextCards.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, error = "No more cards available") }
                    return@launch
                }
                
                cards = nextCards
                buildAndLoadPlaylist()
                
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load next batch") }
            }
        }
    }
    
    override fun onCleared() {
        Log.d("PlaylistVM", "onCleared: Releasing player")
        super.onCleared()
        playlistPlayer.release()
    }
}
