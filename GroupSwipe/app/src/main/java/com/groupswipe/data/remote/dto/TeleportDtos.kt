package com.groupswipe.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.groupswipe.data.remote.api.TravelDestinationSeed
import com.groupswipe.domain.model.Proposal
import com.groupswipe.domain.model.SessionCategory

// ================================================================
// Teleport Public API – DTOs
// ================================================================

/**
 * Odpowiedź wyszukiwania miast przez Teleport.
 * Teleport używa HAL+JSON (Hypertext Application Language),
 * więc linki do zasobów zagnieżdżone są w polach "_links" i "_embedded".
 */
data class TeleportCitiesResponse(
    @SerializedName("_embedded") val embedded: TeleportCitiesEmbedded?,
    @SerializedName("count") val count: Int?
)

data class TeleportCitiesEmbedded(
    @SerializedName("city:search-results") val searchResults: List<TeleportSearchResult>?
)

data class TeleportSearchResult(
    @SerializedName("matching_full_name") val matchingFullName: String?,
    @SerializedName("_links") val links: TeleportSearchResultLinks?
)

data class TeleportSearchResultLinks(
    @SerializedName("city:item") val cityItem: TeleportLink?
)

data class TeleportLink(
    @SerializedName("href") val href: String?
)

// ---- Szczegóły Urban Area (dane o mieście) ----

data class TeleportCityDetail(
    @SerializedName("name") val name: String?,
    @SerializedName("slug") val slug: String?,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("_links") val links: TeleportCityDetailLinks?,
    @SerializedName("_embedded") val embedded: TeleportCityDetailEmbedded?
)

data class TeleportCityDetailLinks(
    @SerializedName("ua:scores") val scoresLink: TeleportLink?,
    @SerializedName("ua:images") val imagesLink: TeleportLink?
)

data class TeleportCityDetailEmbedded(
    @SerializedName("ua:images") val images: TeleportImagesCollection?
)

data class TeleportImagesCollection(
    @SerializedName("photos") val photos: List<TeleportPhoto>?
)

data class TeleportPhoto(
    @SerializedName("image") val image: TeleportPhotoSizes?,
    @SerializedName("attribution") val attribution: TeleportAttribution?
)

data class TeleportPhotoSizes(
    @SerializedName("web") val web: TeleportPhotoUrl?,
    @SerializedName("mobile") val mobile: TeleportPhotoUrl?
)

data class TeleportPhotoUrl(
    @SerializedName("url") val url: String?
)

data class TeleportAttribution(
    @SerializedName("photographer") val photographer: String?
)

// ---- Wyniki jakości życia ----

data class TeleportCityScores(
    @SerializedName("summary") val summary: String?,
    @SerializedName("teleport_city_score") val teleportScore: Double?,
    @SerializedName("categories") val categories: List<TeleportScoreCategory>?
)

data class TeleportScoreCategory(
    @SerializedName("name") val name: String?,
    @SerializedName("score_out_of_10") val scoreOutOf10: Double?,
    @SerializedName("color") val color: String?
)

// UWAGA: builder buildTravelProposal został przeniesiony do WikipediaDtos.kt.
// Teleport API (api.teleport.org) to martwa usługa, dlatego dane o destynacjach
// pobieramy teraz z REST API Wikipedii. Powyższe DTO pozostają nieużywane.
