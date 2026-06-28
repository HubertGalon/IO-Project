package com.groupswipe.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.groupswipe.data.local.dao.SessionHistoryDao
import com.groupswipe.data.local.entities.SessionHistoryEntity
import com.groupswipe.data.repository.AuthRepository
import com.groupswipe.data.repository.Cities
import com.groupswipe.data.repository.FriendsRepository
import com.groupswipe.data.repository.LocationPreferences
import com.groupswipe.data.repository.SessionRepository
import com.groupswipe.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---- UI State ----

data class CreateSessionUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val createdSessionId: String? = null,
    val availableFriends: List<User> = emptyList(),
    val selectedFriendIds: Set<String> = emptySet(),
    val useCity: Boolean = false,
    val selectedCity: String? = null,
    val availableCities: List<String> = Cities.NAMES
)

data class VotingUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val session: Session? = null,
    val proposals: List<Proposal> = emptyList(),
    val isFinished: Boolean = false,
    val votedProposalIds: Set<String> = emptySet(),
    val vetoedProposalIds: Set<String> = emptySet(),
    val vetoUsedUids: Set<String> = emptySet(),
    val currentUid: String? = null
) {
    /** Propozycje nie zawetowane przez nikogo. */
    val activeProposals: List<Proposal> get() = proposals.filter { it.id !in vetoedProposalIds }
    /** Pozostałe do głosowania przez tego użytkownika (bez zawetowanych i już ocenionych). */
    val remainingProposals: List<Proposal> get() = activeProposals.filter { it.id !in votedProposalIds }
    val currentProposal: Proposal? get() = remainingProposals.firstOrNull()
    val nextProposal: Proposal? get() = remainingProposals.getOrNull(1)
    val totalActive: Int get() = activeProposals.size
    val votedCount: Int get() = totalActive - remainingProposals.size
    val hasMore: Boolean get() = remainingProposals.isNotEmpty()
    /** Czy użytkownik może jeszcze zawetować (jedno veto na osobę na grę). */
    val canVeto: Boolean get() = currentUid != null && currentUid !in vetoUsedUids
}

data class ResultsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val session: Session? = null,
    val results: List<ProposalResult> = emptyList(),
    val winner: ProposalResult? = null,
    val isTie: Boolean = false,                       // Remis na pierwszym miejscu
    val tiedProposals: List<Proposal> = emptyList(),  // Kandydaci do koła fortuny
    val tiebreakWinnerId: String? = null              // Wylosowany zwycięzca (null = jeszcze nie kręcono)
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val activeSessions: List<Session> = emptyList(),
    val currentUser: User? = null,
    val pendingRequests: List<FriendRequest> = emptyList()
)

// ---- ViewModel ----

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val friendsRepository: FriendsRepository,
    private val sessionHistoryDao: SessionHistoryDao,
    private val locationPreferences: LocationPreferences
) : ViewModel() {

    private val _createState = MutableStateFlow(CreateSessionUiState())
    val createState: StateFlow<CreateSessionUiState> = _createState.asStateFlow()

    private val _votingState = MutableStateFlow(VotingUiState())
    val votingState: StateFlow<VotingUiState> = _votingState.asStateFlow()

    private val _resultsState = MutableStateFlow(ResultsUiState())
    val resultsState: StateFlow<ResultsUiState> = _resultsState.asStateFlow()

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private var sessionJob: Job? = null
    private var resultsJob: Job? = null
    private var isProposalsLoading = false
    private val archivingInProgress = mutableSetOf<String>()

    init {
        observeHomePage()
        // Wczytaj zapisany wybór źródła lokalizacji
        _createState.update {
            it.copy(useCity = locationPreferences.useCity, selectedCity = locationPreferences.cityName)
        }
    }

    // ---- Home ----

    private fun observeHomePage() {
        viewModelScope.launch {
            // Łączymy sesje z Firestore z lokalną historią: sesja, która trafiła
            // do historii (po wyłonieniu zwycięzcy), znika z listy aktywnych.
            combine(
                sessionRepository.observeUserSessions(),
                sessionHistoryDao.getAllHistory()
            ) { sessions, history ->
                val archivedIds = history.map { it.sessionId }.toSet()
                // Spróbuj zarchiwizować zakończone sesje, których jeszcze nie ma w historii.
                sessions.filter { it.status == SessionStatus.FINISHED && it.id !in archivedIds }
                    .forEach { archiveFinishedSession(it) }
                sessions.filter { it.id !in archivedIds }
            }.collect { active ->
                _homeState.update { it.copy(activeSessions = active) }
            }
        }
        viewModelScope.launch {
            friendsRepository.getPendingRequests().collect { requests ->
                _homeState.update { it.copy(pendingRequests = requests) }
            }
        }
    }

    /**
     * Archiwizuje zakończoną sesję do lokalnej historii – ale dopiero gdy zwycięzca
     * jest wyłoniony (brak remisu albo remis rozstrzygnięty kołem fortuny).
     */
    private fun archiveFinishedSession(session: Session) {
        if (!archivingInProgress.add(session.id)) return // już w trakcie
        viewModelScope.launch {
            try {
                if (sessionHistoryDao.getHistoryById(session.id) != null) return@launch
                val results = sessionRepository.getSessionResults(session).getOrNull() ?: return@launch
                val maxVotes = results.maxOfOrNull { it.yesVotes } ?: 0
                val tiedCount = results.count { it.yesVotes == maxVotes && maxVotes > 0 }
                val unresolvedTie = tiedCount > 1 && session.tiebreakWinnerId.isBlank()
                if (unresolvedTie) return@launch // zwycięzca jeszcze nie wyłoniony

                val winner = results.firstOrNull { it.isWinner }
                sessionHistoryDao.insertHistory(
                    SessionHistoryEntity(
                        sessionId = session.id,
                        title = session.title,
                        category = session.category,
                        hostName = session.hostName,
                        participantNames = session.participantNames,
                        createdAt = session.createdAt,
                        finishedAt = System.currentTimeMillis(),
                        winnerTitle = winner?.proposal?.title,
                        winnerImageUrl = winner?.proposal?.imageUrl,
                        totalProposals = results.size,
                        participantCount = session.participantIds.size
                    )
                )
            } finally {
                archivingInProgress.remove(session.id)
            }
        }
    }

    // ---- Wybór źródła lokalizacji (GPS / miasto) ----

    fun selectLocationMode(useCity: Boolean) {
        locationPreferences.useCity = useCity
        _createState.update { it.copy(useCity = useCity) }
    }

    fun selectCity(name: String) {
        locationPreferences.cityName = name
        locationPreferences.useCity = true
        _createState.update { it.copy(useCity = true, selectedCity = name) }
    }

    fun loadCurrentUser() {
        viewModelScope.launch {
            val uid = authRepository.currentUserId ?: return@launch
            val user = authRepository.getUserProfile(uid)
            _homeState.update { it.copy(currentUser = user) }
        }
    }

    // ---- Create Session ----

    fun loadFriendsForSession() {
        viewModelScope.launch {
            val result = friendsRepository.getFriends()
            if (result.isSuccess) {
                _createState.update { it.copy(availableFriends = result.getOrThrow()) }
            }
        }
    }

    fun toggleFriendSelection(uid: String) {
        _createState.update { state ->
            val newSelected = if (uid in state.selectedFriendIds) {
                state.selectedFriendIds - uid
            } else {
                state.selectedFriendIds + uid
            }
            state.copy(selectedFriendIds = newSelected)
        }
    }

    fun createSession(title: String, category: SessionCategory) {
        if (title.isBlank()) {
            _createState.update { it.copy(errorMessage = "Podaj tytuł sesji") }
            return
        }

        viewModelScope.launch {
            _createState.update { it.copy(isLoading = true, errorMessage = null) }
            val selectedIds = _createState.value.selectedFriendIds.toList()
            val result = sessionRepository.createSession(title.trim(), category, selectedIds)

            _createState.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, createdSessionId = result.getOrThrow())
                } else {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Błąd tworzenia sesji: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun resetCreateState() = _createState.update { CreateSessionUiState() }

    // ---- Voting ----

    fun loadSession(sessionId: String) {
        if (_votingState.value.session?.id == sessionId && sessionJob?.isActive == true) return

        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            _votingState.update { it.copy(isLoading = true, errorMessage = null) }

            sessionRepository.observeSession(sessionId).collect { session ->
                _votingState.update {
                    it.copy(
                        session = session,
                        currentUid = it.currentUid ?: authRepository.currentUserId,
                        // Veto jest nieodwracalne -> łączymy stan lokalny z serwerowym (brak migotania).
                        vetoedProposalIds = it.vetoedProposalIds + (session?.vetoedProposalIds ?: emptyList()),
                        vetoUsedUids = it.vetoUsedUids + (session?.vetoUsedUids ?: emptyList())
                    )
                }

                // KLUCZOWA POPRAWKA: Ładuj tylko gdy: status to VOTING, lista pusta, brak błędu i nie ładujemy już
                val currentState = _votingState.value
                if (session?.status == SessionStatus.VOTING && 
                    currentState.proposals.isEmpty() && 
                    currentState.errorMessage == null && 
                    !isProposalsLoading) {
                    loadProposals(sessionId)
                } else if (session?.status != SessionStatus.VOTING && !isProposalsLoading) {
                    _votingState.update { it.copy(isLoading = false) }
                }

                if (session?.status == SessionStatus.FINISHED) {
                    _votingState.update { it.copy(isFinished = true, isLoading = false) }
                }

                // Jeśli po wetach (także cudzych) nie zostało nic do oceny – oznacz gotowość.
                val st = _votingState.value
                val uid = authRepository.currentUserId
                if (session?.status == SessionStatus.VOTING &&
                    st.proposals.isNotEmpty() &&
                    st.remainingProposals.isEmpty() &&
                    uid != null && uid !in session.finishedVotingUids) {
                    sessionRepository.markUserFinished(sessionId)
                }
            }
        }
    }

    private suspend fun loadProposals(sessionId: String) {
        isProposalsLoading = true
        val result = sessionRepository.getProposals(sessionId)
        _votingState.update {
            if (result.isSuccess) {
                val proposals = result.getOrThrow()
                if (proposals.isEmpty()) {
                    it.copy(isLoading = false, errorMessage = "Brak propozycji w tej sesji")
                } else {
                    it.copy(isLoading = false, proposals = proposals)
                }
            } else {
                it.copy(isLoading = false, errorMessage = "Błąd ładowania propozycji")
            }
        }
        isProposalsLoading = false
    }

    fun castVote(isYes: Boolean) {
        val state = _votingState.value
        val proposal = state.currentProposal ?: return
        val session = state.session ?: return

        val newVoted = state.votedProposalIds + proposal.id
        val remainingAfter = state.activeProposals.filter { it.id !in newVoted }
        val isLast = remainingAfter.isEmpty()

        _votingState.update { it.copy(votedProposalIds = newVoted) }

        viewModelScope.launch {
            sessionRepository.castVote(
                sessionId = session.id,
                proposalId = proposal.id,
                isYes = isYes,
                isLastVote = isLast
            )
        }
    }

    /**
     * Veto bieżącej propozycji – usuwa ją z puli do głosowania dla WSZYSTKICH graczy.
     */
    fun vetoCurrent() {
        val state = _votingState.value
        if (!state.canVeto) return // jedno veto na osobę na grę
        val proposal = state.currentProposal ?: return
        val session = state.session ?: return
        val uid = state.currentUid ?: return

        // Optymistyczne usunięcie + oznaczenie veta jako wykorzystanego.
        _votingState.update {
            it.copy(
                vetoedProposalIds = it.vetoedProposalIds + proposal.id,
                vetoUsedUids = it.vetoUsedUids + uid
            )
        }

        viewModelScope.launch {
            sessionRepository.vetoProposal(session.id, proposal.id)
            val st = _votingState.value
            if (st.remainingProposals.isEmpty()) {
                sessionRepository.markUserFinished(session.id)
            }
        }
    }

    fun startVoting(sessionId: String) {
        viewModelScope.launch {
            sessionRepository.startVoting(sessionId)
        }
    }

    fun joinByCode(code: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = sessionRepository.joinSessionByCode(code)
            if (result.isSuccess) {
                onSuccess(result.getOrThrow())
            } else {
                onError(result.exceptionOrNull()?.message ?: "Błąd dołączania do sesji")
            }
        }
    }

    // ---- Results ----

    fun loadResults(sessionId: String) {
        if (_resultsState.value.session?.id == sessionId && resultsJob?.isActive == true) return

        resultsJob?.cancel()
        resultsJob = viewModelScope.launch {
            _resultsState.update { it.copy(isLoading = true) }

            sessionRepository.observeSession(sessionId).collect { session ->
                if (session != null && session.status == SessionStatus.FINISHED) {
                    val result = sessionRepository.getSessionResults(session)
                    _resultsState.update {
                        if (result.isSuccess) {
                            val results = result.getOrThrow()
                            val maxVotes = results.maxOfOrNull { r -> r.yesVotes } ?: 0
                            val tied = results.filter { r -> r.yesVotes == maxVotes && maxVotes > 0 }
                            it.copy(
                                isLoading = false,
                                session = session,
                                results = results,
                                winner = results.firstOrNull { r -> r.isWinner },
                                isTie = tied.size > 1,
                                tiedProposals = tied.map { r -> r.proposal },
                                tiebreakWinnerId = session.tiebreakWinnerId.ifBlank { null }
                            )
                        } else {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Błąd ładowania wyników"
                            )
                        }
                    }
                } else {
                    _resultsState.update { it.copy(session = session, isLoading = false) }
                }
            }
        }
    }

    /**
     * Losuje zwycięzcę spośród remisujących propozycji (koło fortuny).
     * Zapis przez transakcję – tylko pierwsze losowanie się utrwala, więc wszyscy
     * gracze zobaczą ten sam wynik i ich koła zatrzymają się na tym samym polu.
     */
    fun spinTiebreak(sessionId: String) {
        val tied = _resultsState.value.tiedProposals
        if (tied.isEmpty()) return
        val pick = tied.random()
        viewModelScope.launch {
            sessionRepository.setTiebreakWinner(sessionId, pick.id)
        }
    }

    fun logout() = authRepository.logout()
}
