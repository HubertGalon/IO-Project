package com.groupswipe.data.remote.api

import com.groupswipe.data.remote.dto.TmdbMoviesResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface dla The Movie Database API.
 * Dokumentacja: https://developer.themoviedb.org/docs
 */
interface TmdbApi {

    /**
     * Pobiera popularne filmy (strona 1 = 20 filmów).
     */
    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "pl-PL",
        @Query("page") page: Int = 1
    ): TmdbMoviesResponse

    /**
     * Wyszukuje filmy po frazie.
     */
    @GET("search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "pl-PL",
        @Query("page") page: Int = 1
    ): TmdbMoviesResponse

    /**
     * Pobiera filmy z danego gatunku.
     */
    @GET("discover/movie")
    suspend fun getMoviesByGenre(
        @Query("api_key") apiKey: String,
        @Query("with_genres") genreId: Int,
        @Query("language") language: String = "pl-PL",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbMoviesResponse
}
