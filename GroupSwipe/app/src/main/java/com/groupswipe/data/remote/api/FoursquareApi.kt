package com.groupswipe.data.remote.api

import com.groupswipe.data.remote.dto.FoursquareSearchResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Retrofit interface dla NOWEGO Foursquare Places API.
 * Dokumentacja: https://docs.foursquare.com/fsq-developers-places/reference/place-search
 *
 * Stary endpoint v3 (api.foursquare.com/v3/places) został wycofany 15 maja 2026.
 * Nowy endpoint: https://places-api.foursquare.com/places/search
 *
 * Autoryzacja: nagłówek "Authorization: Bearer <SERVICE_API_KEY>"
 * Wymagany jest też nagłówek wersji: "X-Places-Api-Version: 2025-06-17".
 * UWAGA: stary klucz "fsq3..." NIE działa z nowym API – potrzebny jest Service API Key.
 */
interface FoursquareApi {

    /**
     * Wyszukuje miejsca (restauracje itp.) w pobliżu podanej lokalizacji.
     *
     * @param authorization  "Bearer <SERVICE_API_KEY>"
     * @param version        Data wersji API, np. "2025-06-17"
     * @param ll             Współrzędne "lat,lng" – centrum wyszukiwania
     * @param query          Fraza wyszukiwania (np. "restaurant")
     * @param radius         Promień w metrach (max 100 000)
     * @param limit          Liczba wyników (max 50)
     * @param sort           "RELEVANCE" | "RATING" | "DISTANCE" | "POPULARITY"
     * @param fields         Pola do zwrócenia (m.in. photos, rating, price)
     */
    @GET("places/search")
    suspend fun searchPlaces(
        @Header("Authorization") authorization: String,
        @Header("X-Places-Api-Version") version: String,
        @Header("accept") accept: String = "application/json",
        @Query("ll") ll: String,
        @Query("query") query: String = "restaurant",
        @Query("radius") radius: Int = 5000,
        @Query("limit") limit: Int = 30,
        @Query("sort") sort: String = "POPULARITY",
        @Query("fields") fields: String =
            "fsq_place_id,name,categories,location,tel,website,distance,rating,price,photos,hours"
    ): FoursquareSearchResponse
}
