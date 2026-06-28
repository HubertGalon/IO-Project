package com.groupswipe.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.groupswipe.domain.model.Proposal
import com.groupswipe.domain.model.SessionCategory
import java.text.SimpleDateFormat
import java.util.Locale

// ================================================================
// Ticketmaster Discovery API v2 – DTOs
// ================================================================

data class TicketmasterEventsResponse(
    @SerializedName("_embedded") val embedded: TicketmasterEmbedded?,
    @SerializedName("page") val page: TicketmasterPage?
)

data class TicketmasterEmbedded(
    @SerializedName("events") val events: List<TicketmasterEventDto>?
)

data class TicketmasterPage(
    @SerializedName("size") val size: Int?,
    @SerializedName("totalElements") val totalElements: Int?,
    @SerializedName("number") val number: Int?
)

data class TicketmasterEventDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("locale") val locale: String?,
    @SerializedName("images") val images: List<TicketmasterImageDto>?,
    @SerializedName("dates") val dates: TicketmasterDatesDto?,
    @SerializedName("classifications") val classifications: List<TicketmasterClassificationDto>?,
    @SerializedName("priceRanges") val priceRanges: List<TicketmasterPriceRangeDto>?,
    @SerializedName("_embedded") val embedded: TicketmasterEventEmbedded?,
    @SerializedName("pleaseNote") val pleaseNote: String?,
    @SerializedName("info") val info: String?
) {
    /**
     * Konwertuje event Ticketmaster na domenowy Proposal.
     * Preferuje największe dostępne zdjęcie.
     */
    fun toProposal(sessionId: String): Proposal {
        // Wybierz najlepsze zdjęcie (najszersze dostępne)
        val bestImage = images
            ?.filter { it.url.isNotBlank() }
            ?.maxByOrNull { it.width ?: 0 }
            ?.url ?: ""

        // Wyciągnij dane o lokalizacji z venue
        val venue = embedded?.venues?.firstOrNull()
        val venueName = venue?.name ?: ""
        val venueCity = venue?.city?.name ?: ""
        val venueCountry = venue?.country?.name ?: ""

        // Klasyfikacja (muzyka, sport, sztuka itp.)
        val classification = classifications?.firstOrNull()
        val segment = classification?.segment?.name ?: "Wydarzenie"
        val genre = classification?.genre?.name?.takeIf { it != "Undefined" } ?: ""
        val subGenre = classification?.subGenre?.name?.takeIf { it != "Undefined" } ?: ""

        // Formatowanie daty
        val rawDate = dates?.start?.localDate
        val rawTime = dates?.start?.localTime
        val formattedDate = rawDate?.let { dateStr ->
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = sdf.parse(dateStr)
                val out = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                out.format(date!!)
            } catch (e: Exception) { dateStr }
        } ?: "Data TBD"
        val formattedTime = rawTime?.take(5) ?: ""

        // Cena
        val priceRange = priceRanges?.firstOrNull()
        val priceText = if (priceRange != null) {
            val currency = priceRange.currency ?: ""
            val min = priceRange.min?.let { "%.0f".format(it) }
            val max = priceRange.max?.let { "%.0f".format(it) }
            when {
                min != null && max != null && min != max -> "$min–$max $currency"
                min != null -> "od $min $currency"
                else -> "Ceny na stronie"
            }
        } else "Sprawdź ceny"

        // Opis: preferuj info, potem pleaseNote, potem generuj z metadanych
        val description = info?.take(350)
            ?: pleaseNote?.take(350)
            ?: buildString {
                append("$segment")
                if (genre.isNotBlank()) append(" • $genre")
                if (subGenre.isNotBlank()) append(" • $subGenre")
                if (venueName.isNotBlank()) append("\n📍 $venueName")
                if (venueCity.isNotBlank()) append(", $venueCity")
            }

        val extraInfo = buildMap<String, String> {
            put("type", segment + if (genre.isNotBlank()) " • $genre" else "")
            put("date", "$formattedDate ${formattedTime}".trim())
            put("price", priceText)
            if (venueName.isNotBlank()) put("venue", venueName)
            if (venueCity.isNotBlank()) put("city", venueCity)
        }

        return Proposal(
            id = "${sessionId}_tm_$id",
            sessionId = sessionId,
            title = name,
            description = description,
            imageUrl = bestImage,
            rating = 4.0f,  // Ticketmaster nie udostępnia ocen publiczności – używamy neutralnej wartości
            category = SessionCategory.ACTIVITIES.displayName,
            externalId = id,
            extraInfo = extraInfo,
            detailUrl = url ?: "https://www.ticketmaster.com/event/$id"
        )
    }
}

data class TicketmasterImageDto(
    @SerializedName("url") val url: String,
    @SerializedName("ratio") val ratio: String?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?,
    @SerializedName("fallback") val fallback: Boolean?
)

data class TicketmasterDatesDto(
    @SerializedName("start") val start: TicketmasterStartDate?,
    @SerializedName("status") val status: TicketmasterStatus?
)

data class TicketmasterStartDate(
    @SerializedName("localDate") val localDate: String?,
    @SerializedName("localTime") val localTime: String?,
    @SerializedName("dateTime") val dateTime: String?,
    @SerializedName("dateTBD") val dateTbd: Boolean?,
    @SerializedName("timeTBD") val timeTbd: Boolean?
)

data class TicketmasterStatus(
    @SerializedName("code") val code: String?
)

data class TicketmasterClassificationDto(
    @SerializedName("primary") val primary: Boolean?,
    @SerializedName("segment") val segment: TicketmasterSegment?,
    @SerializedName("genre") val genre: TicketmasterSegment?,
    @SerializedName("subGenre") val subGenre: TicketmasterSegment?
)

data class TicketmasterSegment(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?
)

data class TicketmasterPriceRangeDto(
    @SerializedName("type") val type: String?,
    @SerializedName("currency") val currency: String?,
    @SerializedName("min") val min: Double?,
    @SerializedName("max") val max: Double?
)

data class TicketmasterEventEmbedded(
    @SerializedName("venues") val venues: List<TicketmasterVenueDto>?
)

data class TicketmasterVenueDto(
    @SerializedName("name") val name: String?,
    @SerializedName("city") val city: TicketmasterCity?,
    @SerializedName("country") val country: TicketmasterCountry?,
    @SerializedName("address") val address: TicketmasterAddress?
)

data class TicketmasterCity(
    @SerializedName("name") val name: String?
)

data class TicketmasterCountry(
    @SerializedName("name") val name: String?,
    @SerializedName("countryCode") val countryCode: String?
)

data class TicketmasterAddress(
    @SerializedName("line1") val line1: String?
)
