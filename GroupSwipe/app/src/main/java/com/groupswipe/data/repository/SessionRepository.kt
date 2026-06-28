package com.groupswipe.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.groupswipe.domain.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val proposalRepository: ProposalRepository
) {
    companion object {
        private const val SESSIONS_COLLECTION = "sessions"
        private const val PROPOSALS_COLLECTION = "proposals"
        private const val VOTES_COLLECTION = "votes"
        private const val TAG = "SessionRepo"
    }

    /**
     * Tworzy nową sesję. Jeśli Firestore muli, wyrzuci czytelny błąd.
     */
    suspend fun createSession(
        title: String,
        category: SessionCategory,
        invitedFriendIds: List<String>
    ): Result<String> = try {
        Log.d(TAG, "START: Tworzenie sesji '$title'")
        
        withTimeout(30000) {
            val currentUid = authRepository.currentUserId ?: throw Exception("Błąd: Nie jesteś zalogowany!")
            
            Log.d(TAG, "KROK 1: Pobieram Twój profil...")
            val currentUser = authRepository.getUserProfile(currentUid)
            val hostName = currentUser?.displayName ?: "Użytkownik"

            val sessionId = firestore.collection(SESSIONS_COLLECTION).document().id
            val inviteCode = UUID.randomUUID().toString().take(8).uppercase()
            val session = Session(
                id = sessionId, hostUid = currentUid, hostName = hostName,
                title = title, category = category, status = SessionStatus.WAITING,
                participantIds = (invitedFriendIds + currentUid).distinct(),
                participantNames = mutableMapOf(currentUid to hostName),
                inviteCode = inviteCode, createdAt = System.currentTimeMillis()
            )

            Log.d(TAG, "KROK 2: Zapisuję sesję w Firestore...")
            firestore.collection(SESSIONS_COLLECTION).document(sessionId).set(session.toMap()).await()

            Log.d(TAG, "KROK 3: Pobieram dane z API...")
            // ProposalRepository ma teraz fallback na dane testowe
            val proposals = proposalRepository.fetchProposals(category, sessionId).getOrDefault(emptyList())

            Log.d(TAG, "KROK 4: Zapisuję propozycje (sztuk: ${proposals.size})...")
            val batch = firestore.batch()
            proposals.forEach { p -> 
                val propRef = firestore.collection(PROPOSALS_COLLECTION).document(p.id)
                batch.set(propRef, p.toMap()) 
            }
            batch.update(firestore.collection(SESSIONS_COLLECTION).document(sessionId), "proposalIds", proposals.map { it.id })
            
            batch.commit().await()

            Log.d(TAG, "SUKCES: Sesja gotowa!")
            Result.success(sessionId)
        }
    } catch (e: Exception) {
        val msg = if (e is kotlinx.coroutines.TimeoutCancellationException) 
            "Błąd: Serwer nie odpowiada. WŁĄCZ FIRESTORE API W KONSOLI!" 
            else e.message ?: "Nieznany błąd"
        Log.e(TAG, "BŁĄD: $msg")
        Result.failure(Exception(msg))
    }

    fun observeSession(sessionId: String): Flow<Session?> = callbackFlow {
        val listener = firestore.collection(SESSIONS_COLLECTION).document(sessionId)
            .addSnapshotListener { snapshot, error -> 
                if (error == null) trySend(snapshot?.toSession()) 
            }
        awaitClose { listener.remove() }
    }

    fun observeUserSessions(): Flow<List<Session>> = callbackFlow {
        val uid = authRepository.currentUserId ?: run { trySend(emptyList()); close(); return@callbackFlow }
        val listener = firestore.collection(SESSIONS_COLLECTION)
            .whereArrayContains("participantIds", uid)
            .addSnapshotListener { snapshot, error ->
                val sessions = snapshot?.documents?.mapNotNull { it.toSession() } ?: emptyList()
                trySend(sessions.sortedByDescending { it.createdAt })
            }
        awaitClose { listener.remove() }
    }

    suspend fun getProposals(sessionId: String): Result<List<Proposal>> = try {
        val snapshot = firestore.collection(PROPOSALS_COLLECTION).whereEqualTo("sessionId", sessionId).get().await()
        Result.success(snapshot.documents.mapNotNull { it.toProposal() })
    } catch (e: Exception) { Result.failure(e) }

    suspend fun castVote(
        sessionId: String,
        proposalId: String,
        isYes: Boolean,
        isLastVote: Boolean
    ): Result<Unit> = try {
        val currentUid = authRepository.currentUserId ?: throw Exception("Nie zalogowano")
        val batch = firestore.batch()
        val voteId = "${currentUid}_${proposalId}"
        
        batch.set(firestore.collection(VOTES_COLLECTION).document(voteId), mapOf(
            "uid" to currentUid, "sessionId" to sessionId, "proposalId" to proposalId,
            "isYes" to isYes, "timestamp" to System.currentTimeMillis()
        ))
        
        if (isLastVote) {
            batch.update(firestore.collection(SESSIONS_COLLECTION).document(sessionId), "finishedVotingUids",
                com.google.firebase.firestore.FieldValue.arrayUnion(currentUid))
        }
        
        batch.commit().await()
        if (isLastVote) checkAndFinishSession(sessionId)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    private suspend fun checkAndFinishSession(sessionId: String) {
        try {
            val sessionDoc = firestore.collection(SESSIONS_COLLECTION).document(sessionId).get().await()
            val session = sessionDoc.toSession() ?: return
            if (session.finishedVotingUids.size >= session.participantIds.size) {
                firestore.collection(SESSIONS_COLLECTION).document(sessionId).update("status", SessionStatus.FINISHED.name).await()
            }
        } catch (e: Exception) {}
    }

    /**
     * Veto – usuwa propozycję z puli do głosowania dla WSZYSTKICH graczy
     * i zapisuje, że dany użytkownik wykorzystał już swoje jedno veto (vetoUsedUids).
     * Obie zmiany w jednej aktualizacji (atomowo).
     */
    suspend fun vetoProposal(sessionId: String, proposalId: String): Result<Unit> = try {
        val uid = authRepository.currentUserId ?: throw Exception("Nie zalogowano")
        firestore.collection(SESSIONS_COLLECTION).document(sessionId).update(
            "vetoedProposalIds", com.google.firebase.firestore.FieldValue.arrayUnion(proposalId),
            "vetoUsedUids", com.google.firebase.firestore.FieldValue.arrayUnion(uid)
        ).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    /**
     * Oznacza bieżącego użytkownika jako gotowego (np. gdy po vecie nie ma już nic do głosowania).
     */
    suspend fun markUserFinished(sessionId: String): Result<Unit> = try {
        val currentUid = authRepository.currentUserId ?: throw Exception("Nie zalogowano")
        firestore.collection(SESSIONS_COLLECTION).document(sessionId)
            .update("finishedVotingUids", com.google.firebase.firestore.FieldValue.arrayUnion(currentUid))
            .await()
        checkAndFinishSession(sessionId)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    /**
     * Zapisuje zwycięzcę wylosowanego kołem fortuny. Transakcja gwarantuje, że
     * tylko PIERWSZE losowanie się utrwala – wszyscy gracze zobaczą ten sam wynik.
     */
    suspend fun setTiebreakWinner(sessionId: String, proposalId: String): Result<Unit> = try {
        val ref = firestore.collection(SESSIONS_COLLECTION).document(sessionId)
        firestore.runTransaction<Void?> { tx ->
            val snap = tx.get(ref)
            val current = snap.getString("tiebreakWinnerId") ?: ""
            if (current.isBlank()) {
                tx.update(ref, "tiebreakWinnerId", proposalId)
            }
            null
        }.await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getSessionResults(session: Session): Result<List<ProposalResult>> = try {
        // Pomijamy propozycje zawetowane – nie biorą udziału w wynikach.
        val proposals = getProposals(session.id).getOrThrow()
            .filter { it.id !in session.vetoedProposalIds }
        val votesSnapshot = firestore.collection(VOTES_COLLECTION).whereEqualTo("sessionId", session.id).get().await()
        val votesByProposal = votesSnapshot.documents.groupBy { it.getString("proposalId") ?: "" }

        val results = proposals.map { proposal ->
            val proposalVotes = votesByProposal[proposal.id] ?: emptyList()
            ProposalResult(
                proposal = proposal,
                yesVotes = proposalVotes.count { it.getBoolean("isYes") == true },
                totalVotes = proposalVotes.size
            )
        }.sortedByDescending { it.yesVotes }

        val maxVotes = results.firstOrNull()?.yesVotes ?: 0
        val tiebreakId = session.tiebreakWinnerId

        val finalResults = results.map { r ->
            val isWinner = when {
                // Remis rozstrzygnięty kołem fortuny – wygrywa wylosowana propozycja.
                tiebreakId.isNotBlank() -> r.proposal.id == tiebreakId
                // Brak remisu – wygrywa lider, jeśli ktokolwiek dostał głos TAK.
                maxVotes > 0 -> r.yesVotes == maxVotes && results.count { it.yesVotes == maxVotes } == 1
                else -> false
            }
            r.copy(isWinner = isWinner)
        }
        Result.success(finalResults)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun startVoting(sessionId: String): Result<Unit> = try {
        firestore.collection(SESSIONS_COLLECTION).document(sessionId).update("status", SessionStatus.VOTING.name).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun joinSessionByCode(inviteCode: String): Result<String> = try {
        val currentUid = authRepository.currentUserId ?: throw Exception("Zaloguj się")
        val currentUser = authRepository.getUserProfile(currentUid) ?: throw Exception("Brak profilu")
        val snapshot = firestore.collection(SESSIONS_COLLECTION).whereEqualTo("inviteCode", inviteCode.uppercase()).get().await()
        val doc = snapshot.documents.firstOrNull() ?: throw Exception("Kod nieważny")
        
        firestore.collection(SESSIONS_COLLECTION).document(doc.id).update(
            "participantIds", com.google.firebase.firestore.FieldValue.arrayUnion(currentUid),
            "participantNames.${currentUid}", currentUser.displayName
        ).await()
        Result.success(doc.id)
    } catch (e: Exception) { Result.failure(e) }
}

@Suppress("UNCHECKED_CAST")
fun com.google.firebase.firestore.DocumentSnapshot.toSession(): Session? = try {
    Session(
        id = id, hostUid = getString("hostUid") ?: "", hostName = getString("hostName") ?: "",
        title = getString("title") ?: "", category = SessionCategory.valueOf(getString("category") ?: "MOVIES"),
        status = SessionStatus.valueOf(getString("status") ?: "WAITING"),
        participantIds = (get("participantIds") as? List<String>) ?: emptyList(),
        participantNames = (get("participantNames") as? Map<String, String>) ?: emptyMap(),
        finishedVotingUids = (get("finishedVotingUids") as? List<String>) ?: emptyList(),
        proposalIds = (get("proposalIds") as? List<String>) ?: emptyList(),
        vetoedProposalIds = (get("vetoedProposalIds") as? List<String>) ?: emptyList(),
        vetoUsedUids = (get("vetoUsedUids") as? List<String>) ?: emptyList(),
        tiebreakWinnerId = getString("tiebreakWinnerId") ?: "",
        createdAt = getLong("createdAt") ?: 0L, inviteCode = getString("inviteCode") ?: ""
    )
} catch (e: Exception) { null }

@Suppress("UNCHECKED_CAST")
fun com.google.firebase.firestore.DocumentSnapshot.toProposal(): Proposal? = try {
    Proposal(
        id = id, sessionId = getString("sessionId") ?: "", title = getString("title") ?: "",
        description = getString("description") ?: "", imageUrl = getString("imageUrl") ?: "",
        rating = (getDouble("rating") ?: 0.0).toFloat(), category = getString("category") ?: "",
        externalId = getString("externalId") ?: "", extraInfo = (get("extraInfo") as? Map<String, String>) ?: emptyMap(),
        detailUrl = getString("detailUrl") ?: ""
    )
} catch (e: Exception) { null }

fun Session.toMap(): Map<String, Any> = mapOf(
    "id" to id, "hostUid" to hostUid, "hostName" to hostName, "title" to title,
    "category" to category.name, "status" to status.name, "participantIds" to participantIds,
    "participantNames" to participantNames, "finishedVotingUids" to finishedVotingUids,
    "proposalIds" to proposalIds, "vetoedProposalIds" to vetoedProposalIds,
    "vetoUsedUids" to vetoUsedUids,
    "tiebreakWinnerId" to tiebreakWinnerId,
    "createdAt" to createdAt, "inviteCode" to inviteCode
)

fun Proposal.toMap(): Map<String, Any> = mapOf(
    "id" to id, "sessionId" to sessionId, "title" to title, "description" to description,
    "imageUrl" to imageUrl, "rating" to rating, "category" to category, "externalId" to externalId,
    "extraInfo" to extraInfo, "detailUrl" to detailUrl
)
