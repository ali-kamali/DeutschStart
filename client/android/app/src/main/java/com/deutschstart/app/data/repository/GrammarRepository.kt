package com.deutschstart.app.data.repository

import com.deutschstart.app.data.local.GrammarDao
import com.deutschstart.app.data.local.GrammarTopicEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrammarRepository @Inject constructor(
    private val grammarDao: GrammarDao
) {
    fun getAllTopics(): Flow<List<GrammarTopicEntity>> = grammarDao.getAllTopics()

    suspend fun getTopicById(id: String): GrammarTopicEntity? = grammarDao.getTopicById(id)
}
