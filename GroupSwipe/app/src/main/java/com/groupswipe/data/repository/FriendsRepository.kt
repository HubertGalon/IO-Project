package com.groupswipe.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.groupswipe.domain.model.FriendRequest
import com.groupswipe.domain.model.FriendRequestStatus
import com.groupswipe.domain.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repozytorium obsługujące system znajomych:
 * wysyłanie, odbieranie i akceptowanie zaproszeń.
 */
@Singleton
class FriendsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val FRIEND_REQUESTS = "friend_requests"
        private const val USERS_COLLECTION = "users"
    }

    /**
     * Flow emitujący listę oczekujących zaproszeń dla bieżącego użytkownika.
     */
    fun getPendingRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val uid = authRepository.currentUserId ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection(FRIEND_REQUESTS)
            .whereEqualTo("toUid", uid)
            .whereEqualTo("status", FriendRequestStatus.PENDING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val requests = snapshot?.documents?.mapNotNull { it.toFriendRequest() } ?: emptyList()
                trySend(requests)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Wysyła zaproszenie do znajomych.
     * Sprawdza czy zaproszenie nie istnieje już wcześniej.
     */
    suspend fun sendFriendRequest(toUser: User): Result<Unit> {
        val currentUid = authRepository.currentUserId ?: return Result.failure(Exception("Nie zalogowano"))
        val currentUser = authRepository.getUserProfile(currentUid)
            ?: return Result.failure(Exception("Nie znaleziono profilu"))

        return try {
            // Sprawdź czy zaproszenie już istnieje
            val existing = firestore.collection(FRIEND_REQUESTS)
                .whereEqualTo("fromUid", currentUid)
                .whereEqualTo("toUid", toUser.uid)
                .get().await()

            if (!existing.isEmpty) {
                return Result.failure(Exception("Zaproszenie zostało już wysłane"))
            }

            val requestId = firestore.collection(FRIEND_REQUESTS).document().id
            val request = FriendRequest(
                id = requestId,
                fromUid = currentUid,
                fromName = currentUser.displayName,
                fromEmail = currentUser.email,
                toUid = toUser.uid,
                status = FriendRequestStatus.PENDING
            )

            firestore.collection(FRIEND_REQUESTS)
                .document(requestId)
                .set(request.toMap())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Akceptuje zaproszenie – dodaje obu użytkowników do listy znajomych.
     */
    suspend fun acceptFriendRequest(request: FriendRequest): Result<Unit> {
        val currentUid = authRepository.currentUserId ?: return Result.failure(Exception("Nie zalogowano"))

        return try {
            val batch = firestore.batch()

            // Zaktualizuj status zaproszenia
            val requestRef = firestore.collection(FRIEND_REQUESTS).document(request.id)
            batch.update(requestRef, "status", FriendRequestStatus.ACCEPTED.name)

            // Dodaj do listy znajomych obu użytkowników
            val currentUserRef = firestore.collection(USERS_COLLECTION).document(currentUid)
            val senderRef = firestore.collection(USERS_COLLECTION).document(request.fromUid)

            batch.update(currentUserRef, "friendIds",
                com.google.firebase.firestore.FieldValue.arrayUnion(request.fromUid))
            batch.update(senderRef, "friendIds",
                com.google.firebase.firestore.FieldValue.arrayUnion(currentUid))

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Odrzuca zaproszenie do znajomych.
     */
    suspend fun rejectFriendRequest(request: FriendRequest): Result<Unit> {
        return try {
            firestore.collection(FRIEND_REQUESTS)
                .document(request.id)
                .update("status", FriendRequestStatus.REJECTED.name)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pobiera listę znajomych bieżącego użytkownika.
     */
    suspend fun getFriends(): Result<List<User>> {
        val currentUid = authRepository.currentUserId ?: return Result.failure(Exception("Nie zalogowano"))
        return try {
            val userDoc = firestore.collection(USERS_COLLECTION).document(currentUid).get().await()
            @Suppress("UNCHECKED_CAST")
            val friendIds = (userDoc.get("friendIds") as? List<String>) ?: emptyList()

            if (friendIds.isEmpty()) return Result.success(emptyList())

            // Firestore obsługuje max 30 elementów w whereIn
            val friends = friendIds.chunked(30).flatMap { chunk ->
                firestore.collection(USERS_COLLECTION)
                    .whereIn("uid", chunk)
                    .get().await()
                    .documents.mapNotNull { it.toUser() }
            }
            Result.success(friends)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun FriendRequest.toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "fromUid" to fromUid,
    "fromName" to fromName,
    "fromEmail" to fromEmail,
    "toUid" to toUid,
    "status" to status.name,
    "timestamp" to timestamp
)

fun com.google.firebase.firestore.DocumentSnapshot.toFriendRequest(): FriendRequest? {
    return try {
        FriendRequest(
            id = getString("id") ?: id,
            fromUid = getString("fromUid") ?: "",
            fromName = getString("fromName") ?: "",
            fromEmail = getString("fromEmail") ?: "",
            toUid = getString("toUid") ?: "",
            status = FriendRequestStatus.valueOf(getString("status") ?: "PENDING"),
            timestamp = getLong("timestamp") ?: 0L
        )
    } catch (e: Exception) {
        null
    }
}
