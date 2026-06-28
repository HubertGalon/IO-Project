package com.groupswipe.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.groupswipe.data.repository.AuthRepository
import com.groupswipe.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Aktualny zalogowany użytkownik – null jeśli niezalogowany */
    val currentUser: StateFlow<User?> = authRepository.currentUserFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun login(email: String, password: String) {
        if (!validateLoginInput(email, password)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.login(email.trim(), password)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, isSuccess = true)
                } else {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapFirebaseError(result.exceptionOrNull()?.message)
                    )
                }
            }
        }
    }

    fun register(email: String, password: String, displayName: String, confirmPassword: String) {
        if (!validateRegisterInput(email, password, displayName, confirmPassword)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.register(email.trim(), password, displayName.trim())
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, isSuccess = true)
                } else {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapFirebaseError(result.exceptionOrNull()?.message)
                    )
                }
            }
        }
    }

    fun logout() = authRepository.logout()

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    // ---- Walidacja ----

    private fun validateLoginInput(email: String, password: String): Boolean {
        when {
            email.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Podaj adres email") }
                return false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                _uiState.update { it.copy(errorMessage = "Nieprawidłowy format emaila") }
                return false
            }
            password.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Podaj hasło") }
                return false
            }
            password.length < 6 -> {
                _uiState.update { it.copy(errorMessage = "Hasło musi mieć minimum 6 znaków") }
                return false
            }
        }
        return true
    }

    private fun validateRegisterInput(
        email: String, password: String, displayName: String, confirmPassword: String
    ): Boolean {
        when {
            displayName.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "Podaj swoją nazwę") }
                return false
            }
            displayName.length < 2 -> {
                _uiState.update { it.copy(errorMessage = "Nazwa musi mieć minimum 2 znaki") }
                return false
            }
            !validateLoginInput(email, password) -> return false
            password != confirmPassword -> {
                _uiState.update { it.copy(errorMessage = "Hasła nie są identyczne") }
                return false
            }
        }
        return true
    }

    /**
     * Tłumaczy błędy Firebase Auth na przyjazne komunikaty po polsku.
     */
    private fun mapFirebaseError(message: String?): String = when {
        message == null -> "Nieznany błąd"
        "email address is already in use" in message -> "Ten email jest już zajęty"
        "password is invalid" in message || "wrong-password" in message -> "Nieprawidłowe hasło"
        "no user record" in message || "user-not-found" in message -> "Nie znaleziono konta z tym emailem"
        "network error" in message -> "Błąd sieci – sprawdź połączenie z internetem"
        "too-many-requests" in message -> "Zbyt wiele prób logowania. Spróbuj za chwilę"
        else -> "Błąd: ${message.take(100)}"
    }
}
