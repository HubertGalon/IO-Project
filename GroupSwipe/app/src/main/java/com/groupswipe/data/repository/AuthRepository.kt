package com.groupswipe.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.groupswipe.domain.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val USERS_COLLECTION = "users"
        private const val TAG = "AuthRepository"
    }

    val currentUserFlow: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser == null) {
                trySend(null)
            } else {
                trySend(User(uid = firebaseUser.uid, email = firebaseUser.email ?: ""))
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val currentUserId: String? get() = auth.currentUser?.uid

    suspend fun register(email: String, password: String, displayName: String): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw Exception("Nie udało się utworzyć konta")

            val user = User(
                uid = firebaseUser.uid,
                email = email,
                displayName = displayName
            )
            
            withTimeoutOrNull(10000) {
                firestore.collection(USERS_COLLECTION)
                    .document(firebaseUser.uid)
                    .set(user.toMap())
                    .await()
            } ?: throw Exception("Timeout zapisu profilu w Firestore")

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw Exception("Logowanie nie powiodło się")
            val user = getUserProfile(firebaseUser.uid)
                ?: User(uid = firebaseUser.uid, email = email)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Naprawione wylogowanie
    fun logout() = auth.signOut()

    suspend fun getUserProfile(uid: String): User? {
        return try {
            withTimeoutOrNull(8000) {
                val doc = firestore.collection(USERS_COLLECTION).document(uid).get().await()
                if (doc.exists()) doc.toUser() else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUserProfile error: ${e.message}")
            null
        }
    }

    suspend fun searchUsersByEmail(query: String): Result<List<User>> {
        return try {
            val currentUid = currentUserId ?: return Result.failure(Exception("Nie zalogowano"))
            val snapshot = withTimeoutOrNull(10000) {
                firestore.collection(USERS_COLLECTION)
                    .whereGreaterThanOrEqualTo("email", query)
                    .whereLessThanOrEqualTo("email", query + "\uf8ff")
                    .limit(10)
                    .get()
                    .await()
            } ?: throw Exception("Timeout wyszukiwania")

            val users = snapshot.documents
                .mapNotNull { it.toUser() }
                .filter { it.uid != currentUid }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun User.toMap(): Map<String, Any> = mapOf(
    "uid" to uid,
    "email" to email,
    "displayName" to displayName,
    "photoUrl" to (photoUrl ?: ""),
    "friendIds" to friendIds
)

@Suppress("UNCHECKED_CAST")
fun com.google.firebase.firestore.DocumentSnapshot.toUser(): User? {
    return try {
        User(
            uid = getString("uid") ?: id,
            email = getString("email") ?: "",
            displayName = getString("displayName") ?: "",
            photoUrl = getString("photoUrl")?.ifEmpty { null },
            friendIds = (get("friendIds") as? List<String>) ?: emptyList()
        )
    } catch (e: Exception) {
        null
    }
}
