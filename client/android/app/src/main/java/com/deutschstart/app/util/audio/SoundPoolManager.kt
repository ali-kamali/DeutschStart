package com.deutschstart.app.util.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class SoundPoolManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val soundPool: SoundPool
    private val loadedSounds = mutableMapOf<String, Int>() // path -> soundId
    private val loadingCallbacks = mutableMapOf<Int, (Boolean) -> Unit>() // soundId -> callback

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()
            
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            val success = status == 0
            synchronized(loadingCallbacks) {
                loadingCallbacks.remove(sampleId)?.invoke(success)
            }
        }
    }

    /**
     * Loads a sound file into memory. Suspends until loaded.
     * Returns the soundId if successful, null otherwise.
     */
    suspend fun load(path: String): Int? {
        if (loadedSounds.containsKey(path)) return loadedSounds[path]
        
        val file = File(path)
        if (!file.exists()) return null

        return suspendCancellableCoroutine { cont ->
            val soundId = soundPool.load(path, 1)
            
            synchronized(loadingCallbacks) {
                loadingCallbacks[soundId] = { success ->
                    if (success) {
                        loadedSounds[path] = soundId
                        if (cont.isActive) cont.resume(soundId)
                    } else {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
    }

    fun play(soundId: Int) {
        soundPool.play(soundId, 1f, 1f, 0, 0, 1f)
    }

    fun unload(path: String) {
        loadedSounds[path]?.let { id ->
            soundPool.unload(id)
            loadedSounds.remove(path)
        }
    }
    
    fun release() {
        soundPool.release()
        loadedSounds.clear()
        loadingCallbacks.clear()
    }
}
