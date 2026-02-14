package com.deutschstart.app.ui.grammar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschstart.app.data.local.GrammarTopicEntity
import com.deutschstart.app.data.repository.GrammarRepository
import com.deutschstart.app.util.LinguisticsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GrammarViewModel @Inject constructor(
    private val repository: GrammarRepository,
    val linguisticsEngine: LinguisticsEngine
) : ViewModel() {

    val topics: StateFlow<List<GrammarTopicEntity>> = repository.getAllTopics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTopic = MutableStateFlow<GrammarTopicEntity?>(null)
    val selectedTopic: StateFlow<GrammarTopicEntity?> = _selectedTopic

    fun selectTopic(topicId: String) {
        viewModelScope.launch {
            _selectedTopic.value = repository.getTopicById(topicId)
        }
    }

    fun clearSelection() {
        _selectedTopic.value = null
    }

    // Refresh linguistic cache if needed (e.g. onResume)
    fun refreshLinguistics() {
        viewModelScope.launch {
            linguisticsEngine.reload()
        }
    }
}
