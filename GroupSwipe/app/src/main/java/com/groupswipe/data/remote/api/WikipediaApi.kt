package com.groupswipe.data.remote.api

import com.groupswipe.data.remote.dto.WikipediaSummaryDto
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface dla publicznego REST API Wikipedii.
 * Dokumentacja: https://pl.wikipedia.org/api/rest_v1/
 *
 * Całkowicie DARMOWE, bez klucza API, bez rejestracji, stabilne i niezawodne.
 * Zastępuje wycofaną usługę Teleport jako źródło danych o destynacjach (TRAVEL):
 * dla każdego miasta zwraca zdjęcie (thumbnail) oraz krótki opis (extract).
 */
interface WikipediaApi {

    /**
     * Pobiera podsumowanie artykułu (zdjęcie + opis) dla danego tytułu strony.
     * Przykład: /api/rest_v1/page/summary/Barcelona
     *
     * @param title  Tytuł strony w polskiej Wikipedii (np. "Barcelona", "Rzym", "Nowy Jork")
     */
    @GET("api/rest_v1/page/summary/{title}")
    suspend fun getSummary(@Path("title") title: String): WikipediaSummaryDto
}
