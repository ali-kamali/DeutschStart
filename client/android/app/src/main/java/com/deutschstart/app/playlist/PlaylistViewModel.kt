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
    val error: String? = null
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: LearningRepository,
    private val playlistPlayer: PlaylistPlayer,
    private val playlistBuilder: PlaylistBuilder
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PlaylistUiState(isLoading = true))
    val uiState: StateFlow<PlaylistUiState> = _uiState
    
    private var currentPlaylistData: PlaylistData? = null
    
    init {
        // Observe player state
        viewModelScope.launch {
            combine(
                playlistPlayer.playbackState,
                playlistPlayer.currentCardIndex,
                playlistPlayer.progress
            ) { playbackState, cardIndex, progress ->
                Triple(playbackState, cardIndex, progress)
            }.collect { (playbackState, cardIndex, progress) ->
                _uiState.value = _uiState.value.copy(
                    playbackState = playbackState,
                    currentCardIndex = cardIndex,
                    progress = progress,
                    isLoading = false
                )
            }
        }
    }
    
    fun loadPlaylist(limit: Int = 20, config: PlaylistConfig = PlaylistConfig()) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, config = config)
            
            try {
                // Get due items from repository
                val cards = repository.getDueItems(limit)
                
                if (cards.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No cards due for review"
                    )
                    return@launch
                }
                
                // Build playlist
                val playlistData = playlistBuilder.buildPlaylist(cards)
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
                    error = e.message ?: "Failed to load playlist"
                )
            }
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
        // Reload playlist with new config
        loadPlaylist(limit = _uiState.value.totalCards, config = config)
    }
    
    override fun onCleared() {
        super.onCleared()
        playlistPlayer.release()
    }
}
