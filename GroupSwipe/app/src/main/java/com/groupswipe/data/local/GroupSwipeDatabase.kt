package com.groupswipe.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.groupswipe.data.local.dao.ProposalDao
import com.groupswipe.data.local.dao.SessionHistoryDao
import com.groupswipe.data.local.dao.VoteDao
import com.groupswipe.data.local.entities.ProposalEntity
import com.groupswipe.data.local.entities.RoomConverters
import com.groupswipe.data.local.entities.SessionHistoryEntity
import com.groupswipe.data.local.entities.VoteEntity

@Database(
    entities = [
        SessionHistoryEntity::class,
        ProposalEntity::class,
        VoteEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class GroupSwipeDatabase : RoomDatabase() {
    abstract fun sessionHistoryDao(): SessionHistoryDao
    abstract fun proposalDao(): ProposalDao
    abstract fun voteDao(): VoteDao
}
