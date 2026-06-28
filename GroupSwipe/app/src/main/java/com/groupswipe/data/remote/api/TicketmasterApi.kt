package com.groupswipe.data.remote.api

import com.groupswipe.data.remote.dto.TicketmasterEventsResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface dla Ticketmaster Discovery API v2.
 * Dokumentacja: https://developer.ticketmaster.com/products-and-docs/apis/discovery-api/v2/
 *
 * Klucz API (darmowy): https://developer.ticketmaster.com/
 * Limit darmowy: 5000 żądań/dzień, bez karty kredytowej.
 *
 * Pokrywa wydarzenia kulturalne, sportowe, muzyczne i rozrywkowe na całym świecie.
 */
interface TicketmasterApi {

    /**
     * Wyszukuje nadchodzące wydarzenia w pobliżu lokalizacji.
     *
     * @param apiKey         Klucz API Ticketmaster
     * @param latlong        Współrzędne "lat,lng"
     * @param radius         Promień w km
     * @param unit           Jednostka promienia: "km" | "miles"
     * @param classificationName  Typ wydarzenia: "music", "sports", "arts", "film" itp.
     * @param sort           Sortowanie: "date,asc" | "relevance,desc"
     * @param size           Liczba wyników
     * @param locale         Język wyników
     * @param startDateTime  Format: "2024-01-01T00:00:00Z" – tylko przyszłe eventy
     */
    @GET("discovery/v2/events.json")
    suspend fun searchEvents(
        @Query("apikey") apiKey: String,
        @Query("latlong") latlong: String,
        @Query("radius") radius: Int = 50,
        @Query("unit") unit: String = "km",
        @Query("classificationName") classificationName: String = "music,arts,sports",
        @Query("sort") sort: String = "date,asc",
        @Query("size") size: Int = 10,
        @Query("page") page: Int = 0,
        @Query("locale") locale: String = "*",
        @Query("startDateTime") startDateTime: String? = null
    ): TicketmasterEventsResponse

    /**
     * Wyszukuje wydarzenia po słowie kluczowym i lokalizacji.
     */
    @GET("discovery/v2/events.json")
    suspend fun searchEventsByKeyword(
        @Query("apikey") apiKey: String,
        @Query("keyword") keyword: String,
        @Query("latlong") latlong: String,
        @Query("radius") radius: Int = 100,
        @Query("unit") unit: String = "km",
        @Query("sort") sort: String = "relevance,desc",
        @Query("size") size: Int = 10,
        @Query("locale") locale: String = "*"
    ): TicketmasterEventsResponse
}
