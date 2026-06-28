package com.groupswipe.presentation.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.groupswipe.data.repository.AuthRepository
import com.groupswipe.data.repository.FriendsRepository
import com.groupswipe.domain.model.FriendRequest
import com.groupswipe.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendsUiState(
    val isLoading: Boolean = false,
    val friends: List<User> = emptyList(),
    val pendingRequests: List<FriendRequest> = emptyList(),
    val searchResults: List<User> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendsRepository: FriendsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        loadFriends()
        observePendingRequests()
    }

    private fun loadFriends() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = friendsRepository.getFriends()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    friends = result.getOrDefault(emptyList())
                )
            }
        }
    }

    private fun observePendingRequests() {
        viewModelScope.launch {
            friendsRepository.getPendingRequests().collect { requests ->
                _uiState.update { it.copy(pendingRequests = requests) }
            }
        }
    }

    fun searchUsers(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.length < 3) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val result = authRepository.searchUsersByEmail(query)
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = result.getOrDefault(emptyList())
                )
            }
        }
    }

    fun sendFriendRequest(user: User) {
        viewModelScope.launch {
            val result = friendsRepository.sendFriendRequest(user)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(successMessage = "Zaproszenie wysłane do ${user.displayName}")
                } else {
                    it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Błąd wysyłania zaproszenia")
                }
            }
        }
    }

    fun acceptRequest(request: FriendRequest) {
        viewModelScope.launch {
            friendsRepository.acceptFriendRequest(request)
            loadFriends() // Odśwież listę znajomych
        }
    }

    fun rejectRequest(request: FriendRequest) {
        viewModelScope.launch {
            friendsRepository.rejectFriendRequest(request)
        }
    }

    fun clearMessages() = _uiState.update { it.copy(successMessage = null, errorMessage = null) }
}
