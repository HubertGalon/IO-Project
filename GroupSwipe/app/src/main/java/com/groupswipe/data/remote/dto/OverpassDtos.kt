package com.groupswipe.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.groupswipe.domain.model.Proposal
import com.groupswipe.domain.model.SessionCategory

// ================================================================
// Overpass API (OpenStreetMap) – DTOs
// ================================================================

data class OverpassResponse(
    @SerializedName("elements") val elements: List<OverpassElement>?
)

data class OverpassElement(
    @SerializedName("type") val type: String?,
    @SerializedName("id") val id: Long,
    @SerializedName("lat") val lat: Double?,
    @SerializedName("lon") val lon: Double?,
    @SerializedName("center") val center: OverpassCenter?,
    @SerializedName("tags") val tags: Map<String, String>?
) {
    /**
     * Konwertuje element OSM na Proposal. Zwraca null, gdy miejsce nie ma nazwy
     * (bez nazwy karta byłaby bezużyteczna).
     */
    fun toProposal(sessionId: String, category: SessionCategory): Proposal? {
        val t = tags ?: return null
        val name = t["name"]?.takeIf { it.isNotBlank() } ?: return null

        val street = listOfNotNull(t["addr:street"], t["addr:housenumber"]).joinToString(" ").trim()
        val city = t["addr:city"] ?: t["addr:place"] ?: ""
        val address = listOf(street, city).filter { it.isNotBlank() }.joinToString(", ")
            .ifBlank { "Adres nieznany" }

        val website = (t["website"] ?: t["contact:website"])?.takeIf { it.startsWith("http") } ?: ""
        val phone = t["phone"] ?: t["contact:phone"]
        val cuisine = t["cuisine"]?.replace(";", ", ")?.replace("_", " ")
        val stars = t["stars"]?.toFloatOrNull()?.coerceIn(0f, 5f)

        val typeLabel = if (category == SessionCategory.RESTAURANTS) {
            cuisine?.replaceFirstChar { it.uppercase() } ?: "Restauracja"
        } else {
            (t["tourism"] ?: "hotel").replace("_", " ").replaceFirstChar { it.uppercase() }
        }

        val image = resolveImageUrl(t, id, category)

        val description = t["description"]
            ?: buildString {
                append(typeLabel)
                if (city.isNotBlank()) append(" w $city")
                append(". ")
                append(address)
            }

        val extraInfo = buildMap {
            put("type", typeLabel)
            if (stars != null) put("stars", "★ ${stars.toInt()}")
            if (address.isNotBlank()) put("address", address)
            phone?.let { put("phone", it) }
            t["opening_hours"]?.let { put("hours", it.take(20)) }
        }

        return Proposal(
            id = "${sessionId}_osm_${type}_$id",
            sessionId = sessionId,
            title = name,
            description = description,
            imageUrl = image,
            rating = stars ?: 4.0f,
            category = category.displayName,
            externalId = "$type/$id",
            extraInfo = extraInfo,
            detailUrl = website.ifBlank { "https://www.openstreetmap.org/$type/$id" }
        )
    }
}

data class OverpassCenter(
    @SerializedName("lat") val lat: Double?,
    @SerializedName("lon") val lon: Double?
)

/**
 * Próbuje wskazać REALNE zdjęcie obiektu na podstawie tagów OSM:
 *  1) tag "image" z bezpośrednim adresem URL,
 *  2) tag "wikimedia_commons" (np. "File:Foo.jpg") -> Wikimedia Special:FilePath,
 *  3) fallback: zdjęcie TEMATYCZNE (jedzenie/hotel) z loremflickr, deterministyczne
 *     dla danego obiektu (parametr lock), zamiast losowego, niezwiązanego zdjęcia.
 */
private fun resolveImageUrl(t: Map<String, String>, id: Long, category: SessionCategory): String {
    t["image"]?.let { if (it.startsWith("http")) return it }

    t["wikimedia_commons"]?.let { commons ->
        val file = commons.substringAfter(":", commons).trim()
        if (file.isNotBlank()) {
            return "https://commons.wikimedia.org/wiki/Special:FilePath/" + encodeFile(file)
        }
    }

    val keywords = if (category == SessionCategory.RESTAURANTS) "restaurant,food" else "hotel,room"
    return "https://loremflickr.com/500/750/$keywords?lock=${id % 100000}"
}

private fun encodeFile(name: String): String =
    java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")

