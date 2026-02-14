package com.deutschstart.app.playlist

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.datasource.DefaultDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import dagger.hilt.android.scopes.ViewModelScoped

sealed class PlaybackState {
    data object Idle : PlaybackState()
    data object Loading : PlaybackState()
    data object Playing : PlaybackState()
    data object Paused : PlaybackState()
    data object Completed : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}

@ViewModelScoped
class PlaylistPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private var currentPlaylistData: PlaylistData? = null
    
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState
    
    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex
    
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _germanSpeed = MutableStateFlow(1.0f)
    val germanSpeed: StateFlow<Float> = _germanSpeed
    
    private val _englishSpeed = MutableStateFlow(1.0f)
    val englishSpeed: StateFlow<Float> = _englishSpeed

    private val _currentEffectiveSpeed = MutableStateFlow(1.0f)
    val currentEffectiveSpeed: StateFlow<Float> = _currentEffectiveSpeed

    private val _loopEnabled = MutableStateFlow(false)
    val loopEnabled: StateFlow<Boolean> = _loopEnabled
    
    // Map each segment index to its card index
    private var segmentToCardMap = listOf<Int>()
    
    fun loadPlaylist(playlistData: PlaylistData) {
        release() // Clean up any existing player
        
        currentPlaylistData = playlistData
        _playbackState.value = PlaybackState.Loading
        
        try {
            val player = ExoPlayer.Builder(context).build()
            exoPlayer = player
            
            // Build segment-to-card and type mapping
            buildSegmentMapping(playlistData)
            
            // Build media source with file-existence checks
            val mediaSource = buildMediaSource(playlistData.segments)
            
            // Aggressively reset player state which might be stuck from previous instance
            player.stop()
            player.clearMediaItems()
            
            player.setMediaSource(mediaSource)
            player.prepare()
            
            // Apply loop mode
            player.repeatMode = if (_loopEnabled.value) REPEAT_MODE_ALL else REPEAT_MODE_OFF
            
            // Apply initial speed
            updatePlaybackSpeedForCurrentSegment()
            
            // Set up listener for state changes
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            _playbackState.value = if (player.playWhenReady) {
                                PlaybackState.Playing
                            } else {
                                PlaybackState.Paused
                            }
                        }
                        Player.STATE_ENDED -> {
                            _playbackState.value = PlaybackState.Completed
                        }
                        Player.STATE_BUFFERING -> {
                            // Keep current state
                        }
                        Player.STATE_IDLE -> {
                            _playbackState.value = PlaybackState.Idle
                        }
                    }
                }
                
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playbackState.value = if (isPlaying) {
                        PlaybackState.Playing
                    } else {
                        if (player.playbackState == Player.STATE_ENDED) {
                            PlaybackState.Completed
                        } else {
                            PlaybackState.Paused
                        }
                    }
                    
                    // Update progress and card index
                    updateProgress()
                }
                
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    updateCardIndex()
                }
                
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    updateCardIndex()
                    updatePlaybackSpeedForCurrentSegment()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    // On error, try to skip to next item rather than killing the whole playlist
                    android.util.Log.e("PlaylistPlayer", "Playback error at index ${player.currentMediaItemIndex}: ${error.message}")
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                        player.prepare()
                    } else {
                        _playbackState.value = PlaybackState.Error("Playback error: ${error.message}")
                    }
                }
            })
            
            _playbackState.value = PlaybackState.Paused
            
        } catch (e: Exception) {
            _playbackState.value = PlaybackState.Error(e.message ?: "Unknown error")
        }
    }
    
    
    /**
     * Build media source using DefaultMediaSourceFactory for better resource management.
     */
    private fun buildMediaSource(segments: List<PlaylistSegment>): MediaSource {
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
        val mediaSources = mutableListOf<MediaSource>()
        
        // Log segment count to track growth
        android.util.Log.d("PlaylistPlayer", "Building media source with ${segments.size} segments")
        
        segments.forEach { segment ->
            when (segment) {
                is PlaylistSegment.Audio -> {
                    val file = java.io.File(segment.path)
                    // Check existence AND non-empty (corrupt file protection)
                    if (file.exists() && file.length() > 0) {
                        val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
                        // Use the factory to create the source - handles resources better
                        mediaSources.add(mediaSourceFactory.createMediaSource(mediaItem))
                    } else {
                        android.util.Log.w("PlaylistPlayer", "Audio file invalid (missing/empty), skipping: ${segment.path}")
                        mediaSources.add(SilenceMediaSource(100_000L))
                    }
                }
                is PlaylistSegment.Silence -> {
                    val silenceDurationUs = segment.durationMs * 1000L
                    val silenceSource = SilenceMediaSource(silenceDurationUs)
                    mediaSources.add(silenceSource)
                }
            }
        }
        
        return ConcatenatingMediaSource(*mediaSources.toTypedArray())
    }


    fun setLoopEnabled(enabled: Boolean) {
        _loopEnabled.value = enabled
        exoPlayer?.repeatMode = if (enabled) REPEAT_MODE_ALL else REPEAT_MODE_OFF
    }

    fun setGermanSpeed(speed: Float) {
        _germanSpeed.value = speed
        updatePlaybackSpeedForCurrentSegment()
    }
    
    fun setEnglishSpeed(speed: Float) {
        _englishSpeed.value = speed
        updatePlaybackSpeedForCurrentSegment()
    }
    
    private fun updatePlaybackSpeedForCurrentSegment() {
        val player = exoPlayer ?: return
        val index = player.currentMediaItemIndex
        
        val type = getSegmentType(index)
        val targetSpeed = when (type) {
            SegmentType.GERMAN_WORD, SegmentType.SENTENCE, SegmentType.KAIKKI_AUDIO -> _germanSpeed.value
            SegmentType.TRANSLATION -> _englishSpeed.value
            else -> 1.0f
        }
        
        if (player.playbackParameters.speed != targetSpeed) {
            player.playbackParameters = PlaybackParameters(targetSpeed)
            _currentEffectiveSpeed.value = targetSpeed
        }
    }
    
    fun play() {
        exoPlayer?.play()
    }
    
    fun pause() {
        exoPlayer?.pause()
    }
    
    /**
     * Plays a single audio file immediately, pausing the playlist if active.
     */
    fun playSingleAudio(relativePath: String) {
        // 1. Pause playlist
        pause()
        
        try {
            val file = if (relativePath.startsWith("/")) {
                java.io.File(relativePath) // Already absolute
            } else {
                java.io.File(context.filesDir, "content/$relativePath")
            }
            if (file.exists()) {
                val mp = android.media.MediaPlayer()
                mp.setDataSource(file.absolutePath)
                mp.prepare()
                mp.start()
                mp.setOnCompletionListener { 
                    it.release() 
                }
            } else {
               android.util.Log.e("PlaylistPlayer", "File not found: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaylistPlayer", "Failed to play single audio: $relativePath", e)
        }
    }
    
    fun seekToCard(cardIndex: Int) {
        val playlistData = currentPlaylistData ?: return
        if (cardIndex < 0 || cardIndex >= playlistData.cardCount) return
        
        // Find the first segment that corresponds to this card
        val segmentIndex = segmentToCardMap.indexOfFirst { it == cardIndex }
        
        if (segmentIndex != -1) {
             // Seek to the start of that media item
             exoPlayer?.seekTo(segmentIndex, 0L)
             _currentCardIndex.value = cardIndex
        }
    }
    
    fun skipForward() {
        val current = _currentCardIndex.value
        val playlistData = currentPlaylistData ?: return
        
        if (current < playlistData.cardCount - 1) {
            seekToCard(current + 1)
        }
    }
    
    fun skipBackward() {
        val current = _currentCardIndex.value
        if (current > 0) {
            seekToCard(current - 1)
        }
    }
    
    private fun buildSegmentMapping(playlistData: PlaylistData) {
        // Create mapping: segment index → card index
        val mapping = mutableListOf<Int>()
        var currentCard = 0
        var segmentsForCurrentCard = 0
        
        // Estimate segments per card (German + silence + English + sentence + card gap)
        playlistData.segments.forEachIndexed { index, segment ->
            mapping.add(currentCard)
            segmentsForCurrentCard++
            
            // Heuristic: Card gap is usually ~1500ms, Thinking gap is ~4000ms
            // We only want to increment on Card Gap (end of card)
            // Range 1000..3500 filters out Thinking Gap (4000)
            if (segment is PlaylistSegment.Silence && segment.durationMs in 1000..3500 && segmentsForCurrentCard > 1) {
                currentCard++
                segmentsForCurrentCard = 0
            }
        }
        
        segmentToCardMap = mapping
    }
    
    private fun updateCardIndex() {
        val player = exoPlayer ?: return
        val segmentIndex = player.currentMediaItemIndex
        
        if (segmentIndex >= 0 && segmentIndex < segmentToCardMap.size) {
            val cardIndex = segmentToCardMap[segmentIndex]
            _currentCardIndex.value = cardIndex
        }
    }
    
    private fun updateProgress() {
        val player = exoPlayer ?: return
        val duration = player.duration
        val position = player.currentPosition
        
        if (duration > 0) {
            _progress.value = position.toFloat() / duration.toFloat()
        }
        
        // Also update card index
        updateCardIndex()
    }
    
    private fun getSegmentType(index: Int): SegmentType? {
        val segments = currentPlaylistData?.segments ?: return null
        if (index < 0 || index >= segments.size) return null
        
        val segment = segments[index]
        return if (segment is PlaylistSegment.Audio) segment.type else null
    }
    
    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        _playbackState.value = PlaybackState.Idle
        _currentCardIndex.value = 0
        _progress.value = 0f
    }
}
