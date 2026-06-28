package com.groupswipe.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.groupswipe.data.local.dao.SessionHistoryDao
import com.groupswipe.data.local.entities.SessionHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class HistoryUiState(
    val isLoading: Boolean = true,
    val items: List<SessionHistoryEntity> = emptyList()
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dao: SessionHistoryDao
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = dao.getAllHistory()
        .map { items -> HistoryUiState(isLoading = false, items = items) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HistoryUiState()
        )
}
