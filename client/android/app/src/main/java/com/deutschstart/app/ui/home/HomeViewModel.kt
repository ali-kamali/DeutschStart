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
    val knownWords: Int = 0,
    val comprehension: Float = 0f,
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
        dao.getKnownCount(),
        ticker.flatMapLatest { now -> dao.getDueCount(now) },
        dao.getLeechCount()
    ) { total, learned, known, due, leeches ->
        HomeStats(
            totalWords = total,
            learnedWords = learned, // Keep existing "Learned" stat which includes suspended
            knownWords = known,     // Use "Known" for comprehension (excludes suspended)
            dueWords = due,
            leechCount = leeches,
            comprehension = if (total > 0) known.toFloat() / total.toFloat() else 0f,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeStats(isLoading = true)
    )
}
