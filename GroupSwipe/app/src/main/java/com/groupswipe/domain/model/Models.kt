package com.groupswipe.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Reprezentuje zalogowanego użytkownika aplikacji.
 */
@Parcelize
data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val friendIds: List<String> = emptyList()
) : Parcelable

/**
 * Zaproszenie do znajomych – oczekujące lub zaakceptowane.
 */
data class FriendRequest(
    val id: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val fromEmail: String = "",
    val toUid: String = "",
    val status: FriendRequestStatus = FriendRequestStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis()
)

enum class FriendRequestStatus { PENDING, ACCEPTED, REJECTED }

/**
 * Kategorie sesji grupowej – determinują źródło propozycji.
 */
enum class SessionCategory(val displayName: String, val emoji: String) {
    MOVIES("Filmy", "🎬"),
    RESTAURANTS("Restauracje", "🍽️"),
    HOTELS("Hotele", "🏨"),
    TRAVEL("Wakacje", "✈️"),
    ACTIVITIES("Aktywności", "🎯")
}

/**
 * Status sesji grupowej.
 */
enum class SessionStatus {
    WAITING,   // Oczekiwanie na uczestników
    VOTING,    // Trwa głosowanie
    FINISHED   // Głosowanie zakończone
}

/**
 * Sesja grupowa – główny obiekt synchronizowany przez Firestore.
 */
data class Session(
    val id: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val title: String = "",
    val category: SessionCategory = SessionCategory.MOVIES,
    val status: SessionStatus = SessionStatus.WAITING,
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val finishedVotingUids: List<String> = emptyList(),
    val proposalIds: List<String> = emptyList(),
    val vetoedProposalIds: List<String> = emptyList(), // Propozycje zawetowane przez graczy (usunięte dla wszystkich)
    val vetoUsedUids: List<String> = emptyList(),       // Gracze, którzy wykorzystali już swoje jedno veto
    val tiebreakWinnerId: String = "",                 // Zwycięzca wylosowany kołem fortuny przy remisie
    val createdAt: Long = System.currentTimeMillis(),
    val inviteCode: String = ""
)

/**
 * Pojedyncza propozycja wyświetlana podczas głosowania.
 * Może reprezentować film, restaurację, hotel itp.
 */
@Parcelize
data class Proposal(
    val id: String = "",
    val sessionId: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val rating: Float = 0f,
    val category: String = "",
    val externalId: String = "",        // ID z zewnętrznego API (np. TMDB)
    val extraInfo: Map<String, String> = emptyMap(), // Dodatkowe pola (rok, gatunek itp.)
    val detailUrl: String = ""          // Link do szczegółów / rezerwacji
) : Parcelable

/**
 * Głos użytkownika na propozycję.
 */
data class Vote(
    val uid: String = "",
    val sessionId: String = "",
    val proposalId: String = "",
    val isYes: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Wynik propozycji po zakończeniu sesji – agregacja głosów.
 */
data class ProposalResult(
    val proposal: Proposal,
    val yesVotes: Int,
    val totalVotes: Int,
    val isWinner: Boolean = false
) {
    val percentage: Int get() = if (totalVotes > 0) (yesVotes * 100) / totalVotes else 0
}

/**
 * Zakończona sesja z wynikami – przechowywana w historii.
 */
data class SessionHistory(
    val session: Session,
    val results: List<ProposalResult>,
    val winner: Proposal?
)
