package com.groupswipe.data.local.dao

import androidx.room.*
import com.groupswipe.data.local.entities.ProposalEntity
import com.groupswipe.data.local.entities.SessionHistoryEntity
import com.groupswipe.data.local.entities.VoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionHistoryDao {

    @Query("SELECT * FROM session_history ORDER BY finishedAt DESC")
    fun getAllHistory(): Flow<List<SessionHistoryEntity>>

    @Query("SELECT * FROM session_history WHERE sessionId = :sessionId")
    suspend fun getHistoryById(sessionId: String): SessionHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: SessionHistoryEntity)

    @Delete
    suspend fun deleteHistory(history: SessionHistoryEntity)

    @Query("DELETE FROM session_history")
    suspend fun clearAll()
}

@Dao
interface ProposalDao {

    @Query("SELECT * FROM proposals WHERE sessionId = :sessionId")
    suspend fun getProposalsForSession(sessionId: String): List<ProposalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProposals(proposals: List<ProposalEntity>)

    @Query("DELETE FROM proposals WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}

@Dao
interface VoteDao {

    @Query("SELECT * FROM votes WHERE uid = :uid AND sessionId = :sessionId")
    suspend fun getVotesForSession(uid: String, sessionId: String): List<VoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVote(vote: VoteEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM votes WHERE uid = :uid AND sessionId = :sessionId AND proposalId = :proposalId)")
    suspend fun hasVoted(uid: String, sessionId: String, proposalId: String): Boolean
}
