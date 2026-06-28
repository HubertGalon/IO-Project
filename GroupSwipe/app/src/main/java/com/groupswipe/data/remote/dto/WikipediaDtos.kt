package com.groupswipe.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.groupswipe.data.remote.api.TravelDestinationSeed
import com.groupswipe.domain.model.Proposal
import com.groupswipe.domain.model.SessionCategory

// ================================================================
// Wikipedia REST API – DTOs (źródło danych dla kategorii TRAVEL)
// ================================================================

data class WikipediaSummaryDto(
    @SerializedName("title") val title: String?,
    @SerializedName("extract") val extract: String?,
    @SerializedName("thumbnail") val thumbnail: WikipediaImageDto?,
    @SerializedName("originalimage") val originalImage: WikipediaImageDto?,
    @SerializedName("content_urls") val contentUrls: WikipediaContentUrls?
)

data class WikipediaImageDto(
    @SerializedName("source") val source: String?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)

data class WikipediaContentUrls(
    @SerializedName("desktop") val desktop: WikipediaUrl?
)

data class WikipediaUrl(
    @SerializedName("page") val page: String?
)

/**
 * Buduje Proposal dla destynacji turystycznej na podstawie statycznego seeda
 * (nazwa PL, kraj, czas lotu) wzbogaconego o realne zdjęcie i opis z Wikipedii.
 *
 * @param seed     Seed z predefiniowaną nazwą PL, krajem i czasem lotu
 * @param summary  Podsumowanie z Wikipedii (może być null – wtedy fallback opisowy)
 */
fun buildTravelProposal(
    seed: TravelDestinationSeed,
    summary: WikipediaSummaryDto?,
    sessionId: String
): Proposal {
    // Miniatura ładuje się szybko i pewnie; oryginał (originalimage) bywa wielkości
    // kilku MB i potrafi się nie wyświetlić na karcie.
    val imageUrl = summary?.thumbnail?.source?.takeIf { it.isNotBlank() }
        ?: summary?.originalImage?.source?.takeIf { it.isNotBlank() }
        ?: ""

    val description = summary?.extract
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(400)
        ?.takeIf { it.isNotBlank() }
        ?: "${seed.localName} to popularna destynacja turystyczna w ${seed.country}. " +
        "Idealne miejsce na wspólny wyjazd."

    val detailUrl = summary?.contentUrls?.desktop?.page
        ?: "https://pl.wikipedia.org/wiki/${seed.localName.replace(" ", "_")}"

    val extraInfo = buildMap {
        put("country", seed.country)
        put("flight_time", "✈ ${seed.flightTime}")
    }

    return Proposal(
        id = "${sessionId}_travel_${seed.teleportSlug}",
        sessionId = sessionId,
        title = seed.localName,
        description = description,
        imageUrl = imageUrl,
        rating = 4.5f, // Kuratorska lista popularnych miast – neutralnie wysoka ocena
        category = SessionCategory.TRAVEL.displayName,
        externalId = seed.teleportSlug,
        extraInfo = extraInfo,
        detailUrl = detailUrl
    )
}
