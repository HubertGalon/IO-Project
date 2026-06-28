package com.groupswipe.data.remote.api

import com.groupswipe.data.remote.dto.OverpassResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit interface dla Overpass API (OpenStreetMap).
 *
 * UWAGA: główny serwer overpass-api.de od ~kwietnia 2026 odrzuca część zapytań
 * błędem HTTP 406 (zmiana reguł WAF). Dlatego adres podajemy dynamicznie przez
 * @Url i próbujemy kilku serwerów lustrzanych (patrz ProposalRepository).
 *
 * Wysyłamy nagłówki Accept i User-Agent, bo brak/nietypowy Accept bywa blokowany.
 */
interface OverpassApi {

    @FormUrlEncoded
    @POST
    suspend fun query(
        @Url url: String,
        @Field("data") data: String,
        @Header("Accept") accept: String = "application/json",
        @Header("User-Agent") userAgent: String = "GroupSwipe/1.0"
    ): OverpassResponse
}
