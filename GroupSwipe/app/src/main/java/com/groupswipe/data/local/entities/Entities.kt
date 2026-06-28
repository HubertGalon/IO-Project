package com.groupswipe.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.groupswipe.domain.model.SessionCategory
import com.groupswipe.domain.model.SessionStatus

// ---- Type Converters dla Room ----

class RoomConverters {
    private val gson = Gson()

    @TypeConverter fun fromStringList(value: String): List<String> =
        gson.fromJson(value, object : TypeToken<List<String>>() {}.type)

    @TypeConverter fun toStringList(list: List<String>): String = gson.toJson(list)

    @TypeConverter fun fromStringMap(value: String): Map<String, String> =
        gson.fromJson(value, object : TypeToken<Map<String, String>>() {}.type)

    @TypeConverter fun toStringMap(map: Map<String, String>): String = gson.toJson(map)

    @TypeConverter fun fromSessionCategory(value: String): SessionCategory =
        SessionCategory.valueOf(value)

    @TypeConverter fun toSessionCategory(category: SessionCategory): String = category.name

    @TypeConverter fun fromSessionStatus(value: String): SessionStatus =
        SessionStatus.valueOf(value)

    @TypeConverter fun toSessionStatus(status: SessionStatus): String = status.name
}

// ---- Entity: Zakończona sesja (historia) ----

@Entity(tableName = "session_history")
@TypeConverters(RoomConverters::class)
data class SessionHistoryEntity(
    @PrimaryKey val sessionId: String,
    val title: String,
    val category: SessionCategory,
    val hostName: String,
    val participantNames: Map<String, String>,
    val createdAt: Long,
    val finishedAt: Long,
    val winnerTitle: String?,
    val winnerImageUrl: String?,
    val totalProposals: Int,
    val participantCount: Int
)

// ---- Entity: Propozycja (cache) ----

@Entity(tableName = "proposals")
@TypeConverters(RoomConverters::class)
data class ProposalEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val rating: Float,
    val category: String,
    val externalId: String,
    val extraInfo: Map<String, String>,
    val detailUrl: String
)

// ---- Entity: Głos (cache lokalny) ----

@Entity(tableName = "votes", primaryKeys = ["uid", "sessionId", "proposalId"])
data class VoteEntity(
    val uid: String,
    val sessionId: String,
    val proposalId: String,
    val isYes: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
