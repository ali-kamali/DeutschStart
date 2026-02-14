package com.deutschstart.app.util

import com.deutschstart.app.data.local.VocabularyDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class LinguisticsEngine @Inject constructor(
    private val vocabularyDao: VocabularyDao
) {
    // Word (lowercase) -> Gender (m, f, n)
    private val genderMap = ConcurrentHashMap<String, String>()
    private var isInitialized = false

    fun getGender(word: String): String? {
        // Try exact match first, then lowercase
        return genderMap[word] ?: genderMap[word.lowercase()]
    }

    suspend fun reload() {
        withContext(Dispatchers.IO) {
            val allItems = vocabularyDao.getAllSimple() // We need a simple query for this
            val newMap = HashMap<String, String>()
            allItems.forEach { item ->
                if (item.gender != null) {
                    newMap[item.word] = item.gender
                    newMap[item.word.lowercase()] = item.gender
                }
            }
            genderMap.clear()
            genderMap.putAll(newMap)
            isInitialized = true
        }
    }
}
