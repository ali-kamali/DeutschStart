package com.deutschstart.app.ui.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschstart.app.data.repository.ContentRepository
import com.deutschstart.app.data.repository.ContentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContentViewModel @Inject constructor(
    private val repository: ContentRepository
) : ViewModel() {

    val status: StateFlow<ContentStatus> = repository.status

    fun checkForUpdates() {
        viewModelScope.launch {
            repository.checkForUpdates()
        }
    }

    fun downloadUpdate(filename: String) {
        viewModelScope.launch {
            repository.downloadAndInstall(filename)
        }
    }
}
