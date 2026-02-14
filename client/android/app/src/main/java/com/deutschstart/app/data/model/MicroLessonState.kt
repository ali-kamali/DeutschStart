package com.deutschstart.app.data.model

import com.deutschstart.app.data.local.GrammarTopicEntity
import com.deutschstart.app.data.local.VocabularyEntity

sealed interface MicroLessonUiState {
    object Loading : MicroLessonUiState
    
    data class Active(
        val stage: Stage,
        val vocabProgress: Int = 0,         // 0-5
        val vocabTarget: Int = 5,
        val currentVocabCard: VocabularyEntity? = null,
        val grammarTopic: GrammarTopicEntity? = null,
        val playlistWords: List<VocabularyEntity> = emptyList(),
        val timeRemainingSeconds: Int = 180 // Estimate
    ) : MicroLessonUiState

    data class Completed(val xpBonus: Int) : MicroLessonUiState
    
    object NothingToDo : MicroLessonUiState // No due cards AND no new cards available
    
    enum class Stage(val stageId: Int) {
        VOCAB(0),
        GRAMMAR(1),
        PLAYLIST(2)
    }
}
