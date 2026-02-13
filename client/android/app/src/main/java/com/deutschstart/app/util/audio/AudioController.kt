package com.deutschstart.app.util.audio

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioController @Inject constructor(
    private val soundPoolManager: SoundPoolManager,
    private val exoPlayerManager: ExoPlayerManager
) {
    private val loadedVocabAudio = mutableMapOf<String, Int>() // path -> soundId
    
    /**
     * Pre-loads vocabulary audio for faster playback.
     * Call this when loading a flashcard session.
     */
    suspend fun preloadVocabularyAudio(paths: List<String>) {
        withContext(Dispatchers.IO) {
            paths.forEach { path ->
                if (path.isNotBlank() && !loadedVocabAudio.containsKey(path)) {
                    try {
                        val soundId = soundPoolManager.load(path)
                        if (soundId != null) {
                            loadedVocabAudio[path] = soundId
                        }
                    } catch (e: Exception) {
                        Log.e("AudioController", "Failed to preload: $path", e)
                    }
                }
            }
        }
    }
    
    /**
     * Plays vocabulary audio (short clip from SoundPool).
     */
    fun playVocabularyAudio(path: String) {
        if (path.isBlank()) return
        
        loadedVocabAudio[path]?.let { soundId ->
            soundPoolManager.play(soundId)
        } ?: run {
            Log.w("AudioController", "Audio not preloaded: $path")
        }
    }
    
    /**
     * Plays sentence audio (longer clip from ExoPlayer).
     */
    fun playSentenceAudio(path: String) {
        if (path.isBlank()) return
        exoPlayerManager.play(path)
    }
    
    /**
     * Stops all audio playback.
     */
    fun stop() {
        exoPlayerManager.stop()
    }
    
    /**
     * Unloads vocabulary audio that is no longer needed.
     */
    fun unloadVocabularyAudio(paths: List<String>) {
        paths.forEach { path ->
            loadedVocabAudio.remove(path)
            soundPoolManager.unload(path)
        }
    }
    
    /**
     * Release all audio resources. Call in ViewModel.onCleared().
     */
    fun release() {
        soundPoolManager.release()
        exoPlayerManager.release()
        loadedVocabAudio.clear()
    }
}
