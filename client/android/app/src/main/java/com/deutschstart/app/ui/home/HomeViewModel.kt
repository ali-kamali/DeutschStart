package com.deutschstart.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschstart.app.data.local.VocabularyDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class HomeStats(
    val totalWords: Int = 0,
    val learnedWords: Int = 0,
    val dueWords: Int = 0,
    val leechCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dao: VocabularyDao
) : ViewModel() {

    // Ticker emits current time every 60 seconds to refresh "due" count
    private val ticker = flow {
        emit(System.currentTimeMillis())
        while (true) {
            delay(60_000L)
            emit(System.currentTimeMillis())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeStats> = combine(
        dao.getTotalCount(),
        dao.getLearnedCount(),
        ticker.flatMapLatest { now -> dao.getDueCount(now) },
        dao.getLeechCount()
    ) { total, learned, due, leeches ->
        HomeStats(
            totalWords = total,
            learnedWords = learned,
            dueWords = due,
            leechCount = leeches,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeStats(isLoading = true)
    )
}
