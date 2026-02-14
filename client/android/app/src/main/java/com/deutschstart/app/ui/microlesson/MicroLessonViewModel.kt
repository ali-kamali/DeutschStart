package com.deutschstart.app.ui.microlesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschstart.app.data.local.GrammarDao
import com.deutschstart.app.data.local.VocabularyDao
import com.deutschstart.app.data.local.VocabularyEntity
import com.deutschstart.app.data.model.MicroLessonUiState
import com.deutschstart.app.data.model.XpReason
import com.deutschstart.app.data.repository.GamificationRepository
import com.deutschstart.app.data.repository.LearningRepository
import com.deutschstart.app.data.repository.MicroLessonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MicroLessonViewModel @Inject constructor(
    private val lessonRepo: MicroLessonRepository,
    private val learningRepo: LearningRepository,
    private val gamificationRepo: GamificationRepository,
    private val grammarDao: GrammarDao,
    private val vocabDao: VocabularyDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<MicroLessonUiState>(MicroLessonUiState.Loading)
    val uiState: StateFlow<MicroLessonUiState> = _uiState.asStateFlow()

    // Cache current lesson data
    private var lessonVocabList: List<VocabularyEntity> = emptyList()

    init {
        loadCourse()
    }

    private fun loadCourse() {
        viewModelScope.launch {
            val lesson = lessonRepo.getOrCreateTodaysLesson()
            if (lesson == null) {
                _uiState.value = MicroLessonUiState.NothingToDo
                return@launch
            }

            if (lesson.stage >= 3) {
                 _uiState.value = MicroLessonUiState.Completed(0) // Already done today
                 return@launch
            }

            // Load data needed for state
            // Fetch vocab from stored IDs to ensure consistency across session resumes
            lessonVocabList = lessonRepo.getVocabWords(lesson.vocabWordIds)
            
            // Fallback: If for some reason list is empty (e.g. legacy data), try to fetch new
            if (lessonVocabList.isEmpty()) {
                val now = System.currentTimeMillis()
                val due = vocabDao.getDueItems(now, 5)
                val newNeeded = 5 - due.size
                val newItems = if (newNeeded > 0) vocabDao.getNewItems(newNeeded) else emptyList()
                lessonVocabList = due + newItems
            }

            val grammarTopic = lesson.grammarTopicId?.let { grammarDao.getTopicById(it) }
            val playlistWords = lessonRepo.getPlaylistWords(lesson.playlistWordIds)

            refreshState(
                stage = lesson.stage,
                vocabCompleted = lesson.vocabCompleted,
                grammarTopic = grammarTopic,
                playlistWords = playlistWords
            )
        }
    }

    private suspend fun refreshState(
        stage: Int,
        vocabCompleted: Int,
        grammarTopic: com.deutschstart.app.data.local.GrammarTopicEntity?,
        playlistWords: List<VocabularyEntity>
    ) {
        val stageEnum = mapStage(stage)
        
        // Vocab State Logic
        val currentVocab = if (stage == 0 && lessonVocabList.isNotEmpty() && vocabCompleted < lessonVocabList.size) {
            lessonVocabList[vocabCompleted]
        } else null

        _uiState.value = MicroLessonUiState.Active(
            stage = stageEnum,
            vocabProgress = vocabCompleted,
            vocabTarget = lessonVocabList.size.coerceAtLeast(5), // Default to 5 target
            currentVocabCard = currentVocab,
            grammarTopic = grammarTopic,
            playlistWords = playlistWords
        )
    }

    fun onCardRated(rating: Int) {
        val state = _uiState.value as? MicroLessonUiState.Active ?: return
        if (state.stage != MicroLessonUiState.Stage.VOCAB) return
        val card = state.currentVocabCard ?: return

        viewModelScope.launch {
            // Rate logic
            learningRepo.processResult(card, rating)
            gamificationRepo.awardXp(1, XpReason.SRS_REVIEW) // +1 per card

            val newCompleted = state.vocabProgress + 1
            if (newCompleted >= state.vocabTarget) {
                // Stage Complete
                advanceStage(1) // Go to Grammar
            } else {
                // Next Card
                lessonRepo.updateProgress(
                    stage = 0,
                    vocabCompleted = newCompleted,
                    grammarTopicId = state.grammarTopic?.id,
                    playlistJson = "[]" // Optimization: don't wipe playlist, just ignore
                )
                refreshState(0, newCompleted, state.grammarTopic, state.playlistWords)
            }
        }
    }

    fun onGrammarStageComplete(skipped: Boolean = false) {
        viewModelScope.launch {
            if (!skipped) gamificationRepo.awardXp(5, XpReason.SRS_REVIEW) // Bonus for quiz
            advanceStage(2) // Go to Playlist
        }
    }
    
    fun onPlaylistComplete() {
        viewModelScope.launch {
            gamificationRepo.awardXp(10, XpReason.MICRO_LESSON_BONUS)
            lessonRepo.completeLesson()
            _uiState.value = MicroLessonUiState.Completed(10)
        }
    }

    private suspend fun advanceStage(nextStage: Int) {
        // Logic to skip grammar if null happens inside load/refresh usually, 
        // but here we just blindly go.
        // If nextStage == 1 (Grammar) and no topic, skip to 2.
        
        var actualNext = nextStage
        val currentState = _uiState.value as? MicroLessonUiState.Active
        
        if (actualNext == 1 && currentState?.grammarTopic == null) {
            actualNext = 2
        }

        lessonRepo.updateProgress(
            stage = actualNext,
            vocabCompleted = 0,
            grammarTopicId = currentState?.grammarTopic?.id,
            playlistJson = com.google.gson.Gson().toJson(currentState?.playlistWords?.map { it.id } ?: emptyList<String>())
        )
        refreshState(actualNext, 0, currentState?.grammarTopic, currentState?.playlistWords ?: emptyList())
    }

    private fun mapStage(i: Int): MicroLessonUiState.Stage {
        return when (i) {
            0 -> MicroLessonUiState.Stage.VOCAB
            1 -> MicroLessonUiState.Stage.GRAMMAR
            2 -> MicroLessonUiState.Stage.PLAYLIST
            else -> MicroLessonUiState.Stage.VOCAB
        }
    }
}
