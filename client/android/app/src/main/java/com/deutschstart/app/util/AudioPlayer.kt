package com.deutschstart.app.util

import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null

    suspend fun playFile(path: String) {
        if (path.isBlank()) return
        withContext(Dispatchers.Main) {
            stop()
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    setOnPreparedListener { start() }
                    setOnCompletionListener {
                        release()
                        mediaPlayer = null
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e("AudioPlayer", "MediaPlayer error: what=$what extra=$extra")
                        mp.release()
                        mediaPlayer = null
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error playing file: $path", e)
                mediaPlayer = null
            }
        }
    }

    fun stop() {
        mediaPlayer?.let {
            it.setOnCompletionListener(null)
            it.setOnErrorListener(null)
            try {
                it.release()
            } catch (e: Exception) {
                Log.w("AudioPlayer", "Error releasing MediaPlayer", e)
            }
        }
        mediaPlayer = null
    }
}
