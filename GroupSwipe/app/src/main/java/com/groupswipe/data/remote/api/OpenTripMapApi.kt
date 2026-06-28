package com.groupswipe.data.remote.api

import com.groupswipe.data.remote.dto.OpenTripMapListResponse
import com.groupswipe.data.remote.dto.OpenTripMapPlaceDetail
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface dla OpenTripMap API.
 * Dokumentacja: https://dev.opentripmap.org/docs
 *
 * Klucz API (darmowy bez karty): https://dev.opentripmap.org/
 * Limit: 1000 żądań/dzień dla planu darmowego.
 *
 * OpenTripMap oferuje miliony POI z całego świata skategoryzowanych według OpenStreetMap.
 */
interface OpenTripMapApi {

    /**
     * Pobiera listę miejsc w okolicy na podstawie współrzędnych i promienia.
     *
     * @param apiKey     Klucz API OpenTripMap
     * @param radius     Promień w metrach
     * @param lon        Długość geograficzna centrum
     * @param lat        Szerokość geograficzna centrum
     * @param kinds      Kategorie miejsc (comma-separated):
     *                   "accomodations" = noclegi/hotele
     *                   "interesting_places" = ciekawe miejsca
     *                   "tourist_facilities" = obiekty turystyczne
     * @param rate       Minimalna ocena (0-3, gdzie 3 = najlepsze)
     * @param format     Format odpowiedzi: "json" lub "geojson"
     * @param limit      Liczba wyników
     * @param lang       Język: "pl" | "en" | "de" itp.
     */
    @GET("0.1/en/places/radius")
    suspend fun getPlacesByRadius(
        @Query("apikey") apiKey: String,
        @Query("radius") radius: Int = 10000,
        @Query("lon") lon: Double,
        @Query("lat") lat: Double,
        @Query("kinds") kinds: String,
        @Query("rate") rate: Int = 2,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 15,
        @Query("lang") lang: String = "pl"
    ): OpenTripMapListResponse

    /**
     * Pobiera szczegóły konkretnego miejsca po jego XID (unikalny identyfikator OpenTripMap).
     */
    @GET("0.1/en/places/xid/{xid}")
    suspend fun getPlaceDetail(
        @Path("xid") xid: String,
        @Query("apikey") apiKey: String,
        @Query("lang") lang: String = "pl"
    ): OpenTripMapPlaceDetail
}
