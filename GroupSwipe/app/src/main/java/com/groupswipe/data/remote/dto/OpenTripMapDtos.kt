package com.groupswipe.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.groupswipe.domain.model.Proposal
import com.groupswipe.domain.model.SessionCategory

// ================================================================
// OpenTripMap API – DTOs
// ================================================================

/**
 * Odpowiedź listy miejsc (endpoint /radius lub /bbox).
 * Każdy element to uproszczony obiekt z XID do dalszego pobrania szczegółów.
 */
data class OpenTripMapListResponse(
    @SerializedName("features") val features: List<OpenTripMapFeature>?
) {
    // Kiedy format=json (nie geojson), odpowiedź to bezpośrednia lista
    // Gdy format=geojson, jest opakowana w features
    // Obsługujemy oba przypadki – przy format=json Gson mapuje tablicę bezpośrednio.
}

// Dla format=json odpowiedź to List<OpenTripMapPlaceSummary>
// Typ aliasowy, by Retrofit mógł deserializować bezpośrednią tablicę:
typealias OpenTripMapListJson = List<OpenTripMapPlaceSummary>

data class OpenTripMapFeature(
    @SerializedName("type") val type: String,
    @SerializedName("properties") val properties: OpenTripMapPlaceSummary?,
    @SerializedName("geometry") val geometry: OpenTripMapGeometry?
)

data class OpenTripMapGeometry(
    @SerializedName("type") val type: String,
    @SerializedName("coordinates") val coordinates: List<Double>?
)

/**
 * Uproszczone dane miejsca z endpointu listy.
 * Pełne dane (opis, zdjęcia) pobierane przez osobne żądanie /xid/{xid}.
 */
data class OpenTripMapPlaceSummary(
    @SerializedName("xid") val xid: String,
    @SerializedName("name") val name: String?,
    @SerializedName("rate") val rate: Int?,    // 0-3 (0 = brak oceny, 3 = najwyższe)
    @SerializedName("osm") val osm: String?,
    @SerializedName("kinds") val kinds: String?,
    @SerializedName("dist") val dist: Double?  // Odległość w metrach
)

/**
 * Szczegółowe dane miejsca z endpointu /xid/{xid}.
 */
data class OpenTripMapPlaceDetail(
    @SerializedName("xid") val xid: String,
    @SerializedName("name") val name: String?,
    @SerializedName("rate") val rate: Int?,
    @SerializedName("kinds") val kinds: String?,
    @SerializedName("info") val info: OpenTripMapInfo?,
    @SerializedName("preview") val preview: OpenTripMapPreview?,
    @SerializedName("wikipedia_extracts") val wikipediaExtracts: OpenTripMapWikiExtracts?,
    @SerializedName("address") val address: OpenTripMapAddress?,
    @SerializedName("url") val url: String?,
    @SerializedName("wikipedia") val wikipedia: String?,
    @SerializedName("image") val image: String?
) {
    /**
     * Konwertuje szczegóły miejsca na domenowy Proposal.
     * Ocena OpenTripMap: 0-3 → normalizujemy do 0-5 gwiazdek.
     */
    fun toProposal(sessionId: String, category: SessionCategory): Proposal {
        val displayName = name?.takeIf { it.isNotBlank() } ?: "Nieznane miejsce"

        // Preferuj zdjęcie preview, potem pole image
        val imageUrl = preview?.source?.takeIf { it.isNotBlank() }
            ?: image?.takeIf { it.isNotBlank() }
            ?: ""

        // Opis z Wikipedia lub info
        val description = wikipediaExtracts?.text?.take(400)?.let {
            if (it.length == 400) "$it..." else it
        } ?: info?.descr?.take(400) ?: "Odkryj to wyjątkowe miejsce."

        // Zamień surowe "kinds" na czytelne kategorie
        val kindsList = kinds?.split(",")?.map { it.trim().replace("_", " ") }
            ?: emptyList()
        val primaryKind = kindsList.firstOrNull()?.replaceFirstChar { it.uppercase() }
            ?: category.displayName

        val ratingNormalized = when (rate) {
            1 -> 3.0f
            2 -> 4.0f
            3 -> 5.0f
            else -> 2.5f
        }

        val city = address?.city ?: address?.town ?: address?.village ?: ""
        val country = address?.country ?: ""
        val distKm = address?.let { "" } // dist pochodzi z summary, nie z detail

        val extraInfo = buildMap<String, String> {
            put("type", primaryKind)
            if (city.isNotBlank()) put("city", city)
            if (country.isNotBlank()) put("country", country)
            put("rating_source", "OpenTripMap")
        }

        val detailUrl = wikipedia
            ?: url
            ?: "https://opentripmap.io/topic/$xid"

        return Proposal(
            id = "${sessionId}_otm_$xid",
            sessionId = sessionId,
            title = displayName,
            description = description,
            imageUrl = imageUrl,
            rating = ratingNormalized,
            category = category.displayName,
            externalId = xid,
            extraInfo = extraInfo,
            detailUrl = detailUrl
        )
    }
}

data class OpenTripMapInfo(
    @SerializedName("descr") val descr: String?,
    @SerializedName("image") val image: String?,
    @SerializedName("img_width") val imgWidth: Int?,
    @SerializedName("img_height") val imgHeight: Int?
)

data class OpenTripMapPreview(
    @SerializedName("source") val source: String?,
    @SerializedName("height") val height: Int?,
    @SerializedName("width") val width: Int?
)

data class OpenTripMapWikiExtracts(
    @SerializedName("title") val title: String?,
    @SerializedName("text") val text: String?,
    @SerializedName("html") val html: String?
)

data class OpenTripMapAddress(
    @SerializedName("city") val city: String?,
    @SerializedName("town") val town: String?,
    @SerializedName("village") val village: String?,
    @SerializedName("county") val county: String?,
    @SerializedName("country") val country: String?,
    @SerializedName("country_code") val countryCode: String?,
    @SerializedName("road") val road: String?,
    @SerializedName("house_number") val houseNumber: String?,
    @SerializedName("postcode") val postcode: String?
)
