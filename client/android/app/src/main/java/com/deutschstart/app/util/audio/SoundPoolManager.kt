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
    private var soundPool: SoundPool? = null
    private val loadedSounds = mutableMapOf<String, Int>() // path -> soundId
    private val loadingCallbacks = mutableMapOf<Int, (Boolean) -> Unit>() // soundId -> callback

    init {
        ensureSoundPool()
    }

    /**
     * Ensures the SoundPool is initialized. Re-creates it if previously released.
     */
    private fun ensureSoundPool(): SoundPool {
        soundPool?.let { return it }
        
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        
        val pool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()
            
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            val success = status == 0
            synchronized(loadingCallbacks) {
                loadingCallbacks.remove(sampleId)?.invoke(success)
            }
        }
        
        soundPool = pool
        return pool
    }

    /**
     * Loads a sound file into memory. Suspends until loaded.
     * Returns the soundId if successful, null otherwise.
     */
    suspend fun load(path: String): Int? {
        if (loadedSounds.containsKey(path)) return loadedSounds[path]
        
        val file = File(path)
        if (!file.exists()) return null

        val pool = ensureSoundPool()
        
        return suspendCancellableCoroutine { cont ->
            val soundId = pool.load(path, 1)
            
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
        soundPool?.play(soundId, 1f, 1f, 0, 0, 1f)
    }

    fun unload(path: String) {
        loadedSounds[path]?.let { id ->
            soundPool?.unload(id)
            loadedSounds.remove(path)
        }
    }
    
    fun release() {
        soundPool?.release()
        soundPool = null  // Will be re-created on next use
        loadedSounds.clear()
        loadingCallbacks.clear()
    }
}
