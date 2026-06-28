package com.groupswipe.data.repository

import android.util.Log
import com.groupswipe.BuildConfig
import com.groupswipe.data.remote.api.*
import com.groupswipe.data.remote.dto.buildTravelProposal
import com.groupswipe.domain.model.Proposal
import com.groupswipe.domain.model.SessionCategory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ProposalRepo"

/**
 * Serwery lustrzane Overpass. overpass-api.de bywa obecnie niedostępny (HTTP 406),
 * więc próbujemy alternatyw w kolejności. Wystarczy, że jeden odpowie.
 */
private val OVERPASS_MIRRORS = listOf(
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    "https://overpass-api.de/api/interpreter"
)

@Singleton
class ProposalRepository @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val overpassApi: OverpassApi,
    private val wikipediaApi: WikipediaApi,
    private val ticketmasterApi: TicketmasterApi,
    private val locationProvider: LocationProvider
) {

    suspend fun fetchProposals(category: SessionCategory, sessionId: String): Result<List<Proposal>> =
        when (category) {
            SessionCategory.MOVIES      -> fetchMovies(sessionId)
            SessionCategory.RESTAURANTS -> fetchRestaurants(sessionId)
            SessionCategory.HOTELS      -> fetchHotels(sessionId)
            SessionCategory.TRAVEL      -> fetchTravelDestinations(sessionId)
            SessionCategory.ACTIVITIES  -> fetchActivities(sessionId)
        }

    // ---- FILMY (TMDB) ----
    private suspend fun fetchMovies(sessionId: String): Result<List<Proposal>> = try {
        // Losowa strona popularnych filmów -> różne propozycje za każdym razem.
        val randomPage = (1..15).random()
        val response = withTimeoutOrNull(6000) {
            tmdbApi.getPopularMovies(BuildConfig.TMDB_API_KEY, "pl-PL", randomPage)
        }
        val proposals = response?.results
            ?.filter { it.posterPath != null }
            ?.shuffled()
            ?.take(10)
            ?.map { it.toProposal(sessionId) }
            ?: emptyList()

        if (proposals.isEmpty()) {
            Log.w(TAG, "Filmy: API nie odpowiedziało, dane zastępcze.")
            Result.success(getDummyProposals(sessionId, SessionCategory.MOVIES))
        } else Result.success(proposals)
    } catch (e: Exception) {
        Log.e(TAG, "Filmy (TMDB) błąd: ${e.message}", e)
        Result.success(getDummyProposals(sessionId, SessionCategory.MOVIES))
    }

    // ---- RESTAURACJE (OpenStreetMap / Overpass – darmowe, bez klucza) ----
    private suspend fun fetchRestaurants(sessionId: String): Result<List<Proposal>> = try {
        val location = locationProvider.getCurrentLocation()
        val q = """
            [out:json][timeout:20];
            (
              node["amenity"="restaurant"](around:4000,${location.lat},${location.lng});
              way["amenity"="restaurant"](around:4000,${location.lat},${location.lng});
            );
            out center 60;
        """.trimIndent()
        val proposals = runOverpass(q)
            .mapNotNull { it.toProposal(sessionId, SessionCategory.RESTAURANTS) }
            .distinctBy { it.title }
            .shuffled()
            .take(10)
        if (proposals.isEmpty()) Log.w(TAG, "Restauracje: Overpass pusto/błąd – używam listy kuratorskiej.")
        Result.success(proposals.ifEmpty { CuratedPlaces.restaurants(sessionId) })
    } catch (e: Exception) {
        Log.e(TAG, "Restauracje (Overpass) błąd: ${e.message}", e)
        Result.success(CuratedPlaces.restaurants(sessionId))
    }

    // ---- HOTELE (OpenStreetMap / Overpass) ----
    private suspend fun fetchHotels(sessionId: String): Result<List<Proposal>> = try {
        val location = locationProvider.getCurrentLocation()
        val q = """
            [out:json][timeout:20];
            (
              node["tourism"~"hotel|guest_house|hostel|apartment|motel"](around:9000,${location.lat},${location.lng});
              way["tourism"~"hotel|guest_house|hostel|apartment|motel"](around:9000,${location.lat},${location.lng});
            );
            out center 60;
        """.trimIndent()
        val proposals = runOverpass(q)
            .mapNotNull { it.toProposal(sessionId, SessionCategory.HOTELS) }
            .distinctBy { it.title }
            .shuffled()
            .take(10)
        if (proposals.isEmpty()) Log.w(TAG, "Hotele: Overpass pusto/błąd – używam listy kuratorskiej.")
        Result.success(proposals.ifEmpty { CuratedPlaces.hotels(sessionId) })
    } catch (e: Exception) {
        Log.e(TAG, "Hotele (Overpass) błąd: ${e.message}", e)
        Result.success(CuratedPlaces.hotels(sessionId))
    }

    /**
     * Próbuje kilku serwerów lustrzanych Overpass po kolei (overpass-api.de bywa
     * niedostępny / zwraca 406). Zwraca elementy z pierwszego, który odpowie,
     * albo pustą listę, gdy żaden nie zadziała (wtedy wchodzi lista kuratorska).
     */
    private suspend fun runOverpass(query: String): List<com.groupswipe.data.remote.dto.OverpassElement> {
        for (url in OVERPASS_MIRRORS) {
            val elements = withTimeoutOrNull(5000) {
                runCatching { overpassApi.query(url, query).elements }
                    .onFailure { Log.w(TAG, "Overpass mirror $url: ${it.message}") }
                    .getOrNull()
            }
            if (!elements.isNullOrEmpty()) {
                Log.d(TAG, "Overpass OK: $url (${elements.size} elementów)")
                return elements
            }
        }
        return emptyList()
    }

    // ---- WAKACJE (Wikipedia REST – zastępuje martwy Teleport) ----
    private suspend fun fetchTravelDestinations(sessionId: String): Result<List<Proposal>> = try {
        val destinations = POPULAR_TRAVEL_DESTINATIONS.shuffled().take(10)
        val proposals = coroutineScope {
            destinations.map { seed ->
                async {
                    val summary = withTimeoutOrNull(4000) {
                        runCatching { wikipediaApi.getSummary(seed.localName) }.getOrNull()
                    }
                    // buildTravelProposal ma sensowny fallback nawet gdy summary == null,
                    // dlatego wakacje ZAWSZE zwrócą realną listę miast.
                    buildTravelProposal(seed, summary, sessionId)
                }
            }.awaitAll()
        }
        Result.success(proposals)
    } catch (e: Exception) {
        Log.e(TAG, "Wakacje (Wikipedia) błąd: ${e.message}", e)
        Result.success(getDummyProposals(sessionId, SessionCategory.TRAVEL))
    }

    // ---- AKTYWNOŚCI (Ticketmaster) ----
    private suspend fun fetchActivities(sessionId: String): Result<List<Proposal>> = try {
        val location = locationProvider.getCurrentLocation()
        // Losowa strona + tylko przyszłe wydarzenia -> różne propozycje za każdym razem.
        val randomPage = (0..4).random()
        val nowUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        val response = withTimeoutOrNull(6000) {
            ticketmasterApi.searchEvents(
                apiKey = BuildConfig.TICKETMASTER_API_KEY,
                latlong = location.toQueryString(),
                radius = 100,
                page = randomPage,
                startDateTime = nowUtc
            )
        }
        val events = response?.embedded?.events ?: emptyList()
        val proposals = events.shuffled().take(10).map { it.toProposal(sessionId) }
        if (proposals.isEmpty()) Log.w(TAG, "Aktywności: brak wyników Ticketmaster – dane zastępcze.")
        Result.success(if (proposals.isEmpty()) getDummyProposals(sessionId, SessionCategory.ACTIVITIES) else proposals)
    } catch (e: Exception) {
        Log.e(TAG, "Aktywności (Ticketmaster) błąd: ${e.message}", e)
        Result.success(getDummyProposals(sessionId, SessionCategory.ACTIVITIES))
    }

    // ---- Dane zastępcze (różne dla każdej kategorii, nie zawsze filmy) ----
    private fun placeholder(sessionId: String, idx: Int, title: String, desc: String, category: SessionCategory) =
        Proposal(
            id = "dummy_${category.name}_$idx",
            sessionId = sessionId,
            title = title,
            description = desc,
            imageUrl = "https://picsum.photos/seed/${category.name.lowercase()}$idx/500/750",
            rating = 4f,
            category = category.displayName
        )

    private fun getDummyProposals(sessionId: String, category: SessionCategory): List<Proposal> = when (category) {
        SessionCategory.MOVIES -> listOf(
            Proposal(id = "dummy_movie_1", sessionId = sessionId, title = "Incepcja", description = "Christopher Nolan zaprasza w podróż do wnętrza snu.", imageUrl = "https://image.tmdb.org/t/p/w500/8IBo4jSTZT9o9gD7sqSnnZ69QHx.jpg", rating = 4.4f, category = category.displayName),
            Proposal(id = "dummy_movie_2", sessionId = sessionId, title = "Interstellar", description = "Grupa astronautów podróżuje przez tunel czasoprzestrzenny.", imageUrl = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCUvGxlvSbi1.jpg", rating = 4.3f, category = category.displayName),
            Proposal(id = "dummy_movie_3", sessionId = sessionId, title = "Joker", description = "Studium psychologiczne jednego z najbardziej znanych antagonistów.", imageUrl = "https://image.tmdb.org/t/p/w500/udDclJoHjfjb8Ekgsd4FDteOkCU.jpg", rating = 4.1f, category = category.displayName)
        )
        SessionCategory.RESTAURANTS -> listOf(
            placeholder(sessionId, 1, "Trattoria da Marco", "Klasyczna włoska kuchnia – makarony i pizza z pieca.", category),
            placeholder(sessionId, 2, "Sushi Zen", "Świeże sushi i ramen w spokojnym wnętrzu.", category),
            placeholder(sessionId, 3, "Bistro Zielone", "Sezonowe dania roślinne z lokalnych składników.", category)
        )
        SessionCategory.HOTELS -> listOf(
            placeholder(sessionId, 1, "Hotel Panorama", "Komfortowe pokoje z widokiem na miasto.", category),
            placeholder(sessionId, 2, "Apartamenty Stare Miasto", "Stylowe apartamenty w sercu starówki.", category),
            placeholder(sessionId, 3, "Resort nad Jeziorem", "Spa i wypoczynek z dala od zgiełku.", category)
        )
        SessionCategory.TRAVEL -> listOf(
            placeholder(sessionId, 1, "Barcelona", "Słońce, plaże i architektura Gaudíego.", category),
            placeholder(sessionId, 2, "Rzym", "Wieczne miasto pełne historii.", category),
            placeholder(sessionId, 3, "Lizbona", "Kolorowe uliczki i ocean.", category)
        )
        SessionCategory.ACTIVITIES -> listOf(
            placeholder(sessionId, 1, "Koncert na żywo", "Energetyczny wieczór z muzyką.", category),
            placeholder(sessionId, 2, "Wystawa sztuki", "Współczesne instalacje i obrazy.", category),
            placeholder(sessionId, 3, "Mecz lokalnej drużyny", "Sportowe emocje z trybun.", category)
        )
    }
}
