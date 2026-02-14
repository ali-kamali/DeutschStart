package com.deutschstart.app.ui.leech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschstart.app.data.local.VocabularyDao
import com.deutschstart.app.data.local.VocabularyEntity
import com.deutschstart.app.data.repository.LearningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeechViewModel @Inject constructor(
    private val repository: LearningRepository,
    private val dao: VocabularyDao
) : ViewModel() {
    val leeches: StateFlow<List<VocabularyEntity>> = dao.getLeeches()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun fixLeech(item: VocabularyEntity, mnemonic: String?) {
        viewModelScope.launch { 
            // Only updating mnemonic for now, but could expand to sentences later
            repository.fixLeech(item, mnemonic, null) 
        }
    }

    fun suspendForever(item: VocabularyEntity) {
        viewModelScope.launch { repository.suspendForever(item) }
    }
}
