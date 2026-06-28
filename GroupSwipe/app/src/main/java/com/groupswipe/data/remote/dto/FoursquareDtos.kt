package com.groupswipe.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.groupswipe.domain.model.Proposal
import com.groupswipe.domain.model.SessionCategory

// ================================================================
// Foursquare Places API v3 – DTOs
// ================================================================

data class FoursquareSearchResponse(
    @SerializedName("results") val results: List<FoursquarePlaceDto>
)

data class FoursquarePlaceDto(
    // Nowe API używa "fsq_place_id" zamiast "fsq_id".
    @SerializedName(value = "fsq_place_id", alternate = ["fsq_id"]) val fsqId: String,
    @SerializedName("name") val name: String,
    @SerializedName("categories") val categories: List<FoursquareCategoryDto>,
    @SerializedName("description") val description: String?,
    @SerializedName("rating") val rating: Double?,    // Skala 0-10
    @SerializedName("price") val price: Int?,         // 1=$, 2=$$, 3=$$$, 4=$$$$
    @SerializedName("location") val location: FoursquareLocationDto?,
    @SerializedName("photos") val photos: List<FoursquarePhotoDto>?,
    @SerializedName("hours") val hours: FoursquareHoursDto?,
    @SerializedName("website") val website: String?,
    @SerializedName("tel") val tel: String?
) {
    /**
     * Konwertuje DTO na domenowy Proposal.
     * Foursquare ocenia w skali 0-10 → normalizujemy do 0-5.
     */
    fun toProposal(sessionId: String, category: SessionCategory): Proposal {
        val photoUrl = photos?.firstOrNull()?.let { photo ->
            "${photo.prefix}500x500${photo.suffix}"
        } ?: ""

        val priceSymbol = when (price) {
            1 -> "$"
            2 -> "$$"
            3 -> "$$$"
            4 -> "$$$$"
            else -> "–"
        }

        val primaryCategory = categories.firstOrNull()?.name ?: category.displayName
        val address = location?.formattedAddress ?: location?.address ?: "Adres nieznany"
        val city = location?.locality ?: ""
        val isOpen = hours?.openNow

        val extraInfo = buildMap {
            put("category", primaryCategory)
            put("price", priceSymbol)
            if (address.isNotBlank()) put("address", address)
            if (city.isNotBlank()) put("city", city)
            if (isOpen != null) put("open_now", if (isOpen) "Otwarte" else "Zamknięte")
            tel?.let { put("phone", it) }
        }

        val desc = description
            ?: "Odkryj ${name} – ${primaryCategory.lowercase()} w ${city.ifBlank { "Twojej okolicy" }}."

        return Proposal(
            id = "${sessionId}_fsq_$fsqId",
            sessionId = sessionId,
            title = name,
            description = desc,
            imageUrl = photoUrl,
            rating = ((rating ?: 0.0) / 2.0).toFloat().coerceIn(0f, 5f),
            category = category.displayName,
            externalId = fsqId,
            extraInfo = extraInfo,
            detailUrl = website ?: "https://foursquare.com/v/$fsqId"
        )
    }
}

data class FoursquareCategoryDto(
    @SerializedName(value = "fsq_category_id", alternate = ["id"]) val id: String?,
    @SerializedName("name") val name: String,
    @SerializedName("icon") val icon: FoursquareCategoryIconDto?
)

data class FoursquareCategoryIconDto(
    @SerializedName("prefix") val prefix: String,
    @SerializedName("suffix") val suffix: String
)

data class FoursquareLocationDto(
    @SerializedName("address") val address: String?,
    @SerializedName("locality") val locality: String?,
    @SerializedName("region") val region: String?,
    @SerializedName("country") val country: String?,
    @SerializedName("formatted_address") val formattedAddress: String?
)

data class FoursquarePhotoDto(
    @SerializedName("prefix") val prefix: String,
    @SerializedName("suffix") val suffix: String,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)

data class FoursquareHoursDto(
    @SerializedName("open_now") val openNow: Boolean?
)
