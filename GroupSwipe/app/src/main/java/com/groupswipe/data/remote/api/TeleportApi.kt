package com.groupswipe.data.remote.api

import com.groupswipe.data.remote.dto.TeleportCitiesResponse
import com.groupswipe.data.remote.dto.TeleportCityDetail
import com.groupswipe.data.remote.dto.TeleportCityScores
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit interface dla Teleport Public API.
 * Dokumentacja: https://developers.teleport.org/api/
 *
 * Całkowicie DARMOWE, bez klucza API, bez rejestracji.
 * Dane o jakości życia, kosztach i atrakcyjności 266 miast świata.
 *
 * Używane do kategorii TRAVEL – dostarcza realne dane o destynacjach.
 */
interface TeleportApi {

    /**
     * Wyszukuje miasta po nazwie.
     * Zwraca listę pasujących miast z linkami do szczegółów.
     */
    @GET("cities/")
    suspend fun searchCities(
        @Query("search") query: String,
        @Query("embed") embed: String = "city:search-results/city:item/"
    ): TeleportCitiesResponse

    /**
     * Pobiera wyniki jakości życia dla danego miasta (urban area).
     * URL jest dynamiczny – pochodzi z odpowiedzi searchCities.
     *
     * Przykład: https://api.teleport.org/api/urban_areas/slug:warsaw/scores/
     */
    @GET
    suspend fun getCityScores(@Url url: String): TeleportCityScores

    /**
     * Pobiera szczegółowe dane o urban area (zdjęcia, opis, statystyki).
     */
    @GET
    suspend fun getCityDetail(@Url url: String): TeleportCityDetail
}

/**
 * Predefiniowana lista 20 popularnych destynacji turystycznych używana
 * jako seed przy braku lokalizacji użytkownika lub jako fallback.
 *
 * Dla każdej destynacji znamy slug Teleport API, co pozwala pobrać
 * realne dane (oceny, koszty życia, klimat, zdjęcia).
 */
val POPULAR_TRAVEL_DESTINATIONS = listOf(
    TravelDestinationSeed("Barcelona",    "barcelona",    "Hiszpania",  "3h"),
    TravelDestinationSeed("Amsterdam",    "amsterdam",    "Holandia",   "2.5h"),
    TravelDestinationSeed("Praga",        "prague",       "Czechy",     "1.5h"),
    TravelDestinationSeed("Rzym",         "rome",         "Włochy",     "2.5h"),
    TravelDestinationSeed("Lizbona",      "lisbon",       "Portugalia", "4h"),
    TravelDestinationSeed("Wiedeń",       "vienna",       "Austria",    "1h"),
    TravelDestinationSeed("Budapeszt",    "budapest",     "Węgry",      "1h"),
    TravelDestinationSeed("Dubrownik",    "dubrovnik",    "Chorwacja",  "2h"),
    TravelDestinationSeed("Kopenhaga",    "copenhagen",   "Dania",      "2h"),
    TravelDestinationSeed("Dubaj",        "dubai",        "UAE",        "6h"),
    TravelDestinationSeed("Nowy Jork",    "new-york-city","USA",        "10h"),
    TravelDestinationSeed("Bangkok",      "bangkok",      "Tajlandia",  "11h"),
    TravelDestinationSeed("Tokio",        "tokyo",        "Japonia",    "12h"),
    TravelDestinationSeed("Ateny",        "athens",       "Grecja",     "3h"),
    TravelDestinationSeed("Madryt",       "madrid",       "Hiszpania",  "3h"),
    TravelDestinationSeed("Berlin",       "berlin",       "Niemcy",     "1.5h"),
    TravelDestinationSeed("Paryż",        "paris",        "Francja",    "2.5h"),
    TravelDestinationSeed("Londyn",       "london",       "UK",         "2.5h"),
    TravelDestinationSeed("Singapur",     "singapore",    "Singapur",   "13h"),
    TravelDestinationSeed("Sydney",       "sydney",       "Australia",  "22h")
)

data class TravelDestinationSeed(
    val localName: String,
    val teleportSlug: String,
    val country: String,
    val flightTime: String
)
