package com.deutschstart.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.deutschstart.app.data.local.AppDatabase
import com.deutschstart.app.data.local.VocabularyDao
import com.deutschstart.app.data.local.VocabularyEntity
import com.deutschstart.app.data.remote.ContentApiService
import com.deutschstart.app.util.ZipUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val api: ContentApiService,
    private val db: AppDatabase,
    private val dao: VocabularyDao,
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("content_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _status = MutableStateFlow<ContentStatus>(ContentStatus.Idle)
    val status: StateFlow<ContentStatus> = _status

    suspend fun checkForUpdates() {
        _status.value = ContentStatus.Checking
        try {
            val response = api.getLatestPackMetadata()
            if (response.isSuccessful && response.body() != null) {
                val metadata = response.body()!!
                val currentVersion = prefs.getString("current_version", null)

                if (currentVersion != metadata.filename) {
                    _status.value =
                        ContentStatus.UpdateAvailable(metadata.filename, metadata.size)
                } else {
                    // Check if DB is actually populated (e.g. after destructive migration)
                    val count = dao.getTotalCount().first()
                    if (count == 0) {
                        prefs.edit().remove("current_version").apply()
                        _status.value = ContentStatus.UpdateAvailable(metadata.filename, metadata.size)
                    } else {
                        _status.value = ContentStatus.UpToDate
                    }
                }
            } else {
                _status.value =
                    ContentStatus.Error("Failed to check updates: ${response.code()}")
            }
        } catch (e: Exception) {
            _status.value = ContentStatus.Error("Network error: ${e.message}")
        }
    }

    suspend fun downloadAndInstall(filename: String) {
        _status.value = ContentStatus.Downloading(0f)
        withContext(Dispatchers.IO) {
            try {
                // 1. Download
                val response = api.downloadPack("api/v1/packs/$filename")
                if (!response.isSuccessful || response.body() == null) {
                    _status.value = ContentStatus.Error("Download failed")
                    return@withContext
                }

                val body = response.body()!!
                val zipFile = File(context.cacheDir, "temp_pack.zip")
                val totalBytes = body.contentLength()

                // Use .use {} to guarantee stream closure even on error
                body.byteStream().use { input ->
                    zipFile.outputStream().use { output ->
                        var bytesCopied: Long = 0
                        val buffer = ByteArray(8 * 1024)
                        var bytes = input.read(buffer)

                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            if (totalBytes > 0) {
                                _status.value = ContentStatus.Downloading(
                                    bytesCopied.toFloat() / totalBytes.toFloat()
                                )
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }

                // 2. Unzip
                _status.value = ContentStatus.Installing("Unzipping...")
                val contentDir = File(context.filesDir, "content")
                if (contentDir.exists()) contentDir.deleteRecursively()
                contentDir.mkdirs()

                ZipUtils.unzip(zipFile, contentDir)

                // 3. Import to DB
                _status.value = ContentStatus.Installing("Importing to Database...")
                importToDb(contentDir)

                // 4. Cleanup temp file
                zipFile.delete()

                // 5. Success
                prefs.edit().putString("current_version", filename).apply()
                _status.value = ContentStatus.Success

            } catch (e: Exception) {
                Log.e("ContentRepo", "Install failed", e)
                _status.value = ContentStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun importToDb(contentDir: File) {
        val vocabFile = File(contentDir, "vocabulary.json")
        if (!vocabFile.exists()) throw Exception("Invalid pack: vocabulary.json missing")

        val jsonString = vocabFile.readText()
        val type = object : TypeToken<List<VocabularyJsonItem>>() {}.type
        val items: List<VocabularyJsonItem> = gson.fromJson(jsonString, type)

        val entities = items.map { it.toEntity(contentDir.absolutePath, gson) }

        // Use withTransaction (suspend-safe) instead of runInTransaction
        db.withTransaction {
            dao.deleteAll()
            dao.insertAll(entities)
        }
    }
}

// Status sealed class
sealed class ContentStatus {
    data object Idle : ContentStatus()
    data object Checking : ContentStatus()
    data object UpToDate : ContentStatus()
    data class UpdateAvailable(val version: String, val size: Long) : ContentStatus()
    data class Downloading(val progress: Float) : ContentStatus()
    data class Installing(val stage: String) : ContentStatus()
    data object Success : ContentStatus()
    data class Error(val message: String) : ContentStatus()
}

// JSON mapping DTO
data class VocabularyJsonItem(
    val id: String,
    val word: String,
    val article: String?,
    val gender: String?,
    val plural: String?,
    val pos: String,
    val trans_en: String,
    val audio: String?,
    val audio_en: String?,  // NEW: English translation audio
    val sentences: List<Map<String, Any>>?,
    val kaikki_audio: String?,
    val kaikki_data: Map<String, Any>?
) {
    fun toEntity(basePath: String, gson: Gson): VocabularyEntity {
        val localAudioPath = audio?.let { "$basePath/$it" } ?: ""
        val localEnAudioPath = audio_en?.let { "$basePath/$it" } ?: ""
        val localKaikkiAudioPath = kaikki_audio?.let { "$basePath/$it" }

        val exSentencesList = sentences?.map {
            mapOf(
                "german" to (it["german"] as? String ?: ""),
                "english" to (it["english"] as? String ?: ""),
                "audio_path" to ((it["audio_path"] as? String)?.let { s -> "$basePath/$s" } ?: "")
            )
        } ?: emptyList()

        val jsonSentences = gson.toJson(exSentencesList)

        return VocabularyEntity(
            id = id,
            word = word,
            article = article,
            translationEn = trans_en,
            partOfSpeech = pos,
            gender = gender,
            pluralForm = plural,
            audioLearnPath = localAudioPath,
            audioReviewPath = localAudioPath,
            audioEnPath = localEnAudioPath,  // NEW
            exampleSentencesJson = jsonSentences,
            ipa = null,
            verbPrefix = null,
            verbStem = null,
            frequencyRank = 0,
            category = "General",
            genderMnemonic = null,
            lastReviewedAt = null,
            kaikkiAudioPath = localKaikkiAudioPath,
            kaikkiDataJson = kaikki_data?.let { gson.toJson(it) }
        )
    }
}
