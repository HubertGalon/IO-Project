package com.groupswipe.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.groupswipe.BuildConfig
import com.groupswipe.domain.model.Proposal
import com.groupswipe.domain.model.SessionCategory

data class TmdbMoviesResponse(
    @SerializedName("results") val results: List<TmdbMovieDto>,
    @SerializedName("total_pages") val totalPages: Int,
    @SerializedName("total_results") val totalResults: Int
)

data class TmdbMovieDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("overview") val overview: String,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("vote_average") val voteAverage: Double,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("genre_ids") val genreIds: List<Int>
) {
    /**
     * Konwertuje DTO z API na domenowy model Proposal.
     */
    fun toProposal(sessionId: String): Proposal {
        val year = releaseDate?.take(4) ?: "?"
        val imageUrl = posterPath?.let { "${BuildConfig.TMDB_IMAGE_BASE_URL}$it" } ?: ""

        return Proposal(
            id = "${sessionId}_movie_$id",
            sessionId = sessionId,
            title = title,
            description = overview.take(300).let { if (overview.length > 300) "$it..." else it },
            imageUrl = imageUrl,
            rating = (voteAverage / 2).toFloat(), // TMDB ocenia w skali 10, normalizujemy do 5
            category = SessionCategory.MOVIES.displayName,
            externalId = id.toString(),
            extraInfo = mapOf(
                "year" to year,
                "tmdb_rating" to String.format("%.1f", voteAverage)
            ),
            detailUrl = "https://www.themoviedb.org/movie/$id"
        )
    }
}

/**
 * Pomocnicza mapa TMDB genre_id → nazwa gatunku po polsku.
 */
val TMDB_GENRES = mapOf(
    28 to "Akcja",
    12 to "Przygodowy",
    16 to "Animacja",
    35 to "Komedia",
    80 to "Kryminał",
    99 to "Dokumentalny",
    18 to "Dramat",
    10751 to "Familijny",
    14 to "Fantasy",
    36 to "Historyczny",
    27 to "Horror",
    10402 to "Muzyczny",
    9648 to "Tajemnica",
    10749 to "Romans",
    878 to "Science Fiction",
    53 to "Thriller",
    10752 to "Wojenny",
    37 to "Western"
)
