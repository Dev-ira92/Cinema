package com.cinema.data.model

import com.google.gson.annotations.SerializedName

data class TmdbSearchResponse(
    val page: Int = 1,
    val results: List<MediaItem> = emptyList(),
    @SerializedName("total_results") val totalResults: Int = 0
)

data class MediaItem(
    val id: Int = 0,
    @SerializedName("media_type") val mediaType: String = "movie",
    val title: String = "",
    val name: String = "",
    @SerializedName("original_title") val originalTitle: String = "",
    @SerializedName("original_name") val originalName: String = "",
    val overview: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("release_date") val releaseDate: String = "",
    @SerializedName("first_air_date") val firstAirDate: String = "",
    @SerializedName("vote_average") val rating: Double = 0.0,
    @SerializedName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0
) {
    val displayTitle: String get() = title.ifBlank { name }
    val displayDate: String get() = releaseDate.ifBlank { firstAirDate }
    val year: String get() = displayDate.take(4)
    val normalizedType: String get() = if (mediaType == "tv") "tv" else "movie"
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    val backdropUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
    val ratingText: String get() = if (rating > 0) "%.1f".format(rating) else "–"
}

data class MovieDetail(
    val id: Int = 0,
    val title: String = "",
    @SerializedName("original_title") val originalTitle: String = "",
    val overview: String = "",
    val tagline: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("release_date") val releaseDate: String = "",
    @SerializedName("vote_average") val rating: Double = 0.0,
    val runtime: Int = 0,
    val genres: List<Genre> = emptyList(),
    val credits: Credits? = null,
    @SerializedName("imdb_id") val imdbId: String? = null
) {
    val year: String get() = releaseDate.take(4)
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    val backdropUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
    val ratingText: String get() = if (rating > 0) "%.1f".format(rating) else "–"
    val runtimeText: String get() = if (runtime > 0) "${runtime / 60}h ${runtime % 60}m" else ""
    val genreText: String get() = genres.joinToString(" · ") { it.name }
}

data class TvDetail(
    val id: Int = 0,
    val name: String = "",
    @SerializedName("original_name") val originalName: String = "",
    val overview: String = "",
    val tagline: String = "",
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String = "",
    @SerializedName("vote_average") val rating: Double = 0.0,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int = 0,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int = 0,
    val seasons: List<TvSeasonSummary> = emptyList(),
    val genres: List<Genre> = emptyList(),
    @SerializedName("external_ids") val externalIds: ExternalIds? = null
) {
    val year: String get() = firstAirDate.take(4)
    val imdbId: String? get() = externalIds?.imdbId
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    val backdropUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
    val ratingText: String get() = if (rating > 0) "%.1f".format(rating) else "–"
    val genreText: String get() = genres.joinToString(" · ") { it.name }
}

data class ExternalIds(
    @SerializedName("imdb_id") val imdbId: String? = null
)

data class TvSeasonSummary(
    val id: Int = 0,
    val name: String = "",
    @SerializedName("season_number") val seasonNumber: Int = 0,
    @SerializedName("episode_count") val episodeCount: Int = 0,
    @SerializedName("air_date") val airDate: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    val overview: String = ""
)

data class TvSeasonDetail(
    val id: Int = 0,
    val name: String = "",
    @SerializedName("season_number") val seasonNumber: Int = 0,
    val episodes: List<TvEpisode> = emptyList()
)

data class TvEpisode(
    val id: Int = 0,
    val name: String = "",
    val overview: String = "",
    @SerializedName("episode_number") val episodeNumber: Int = 0,
    @SerializedName("season_number") val seasonNumber: Int = 0,
    @SerializedName("air_date") val airDate: String? = null,
    @SerializedName("still_path") val stillPath: String? = null,
    @SerializedName("vote_average") val rating: Double = 0.0
) {
    val code: String get() = "S%02dE%02d".format(seasonNumber, episodeNumber)
    val stillUrl: String? get() = stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
}

data class Genre(
    val id: Int = 0,
    val name: String = ""
)

data class Credits(
    val cast: List<CastMember> = emptyList(),
    val crew: List<CrewMember> = emptyList()
)

data class CastMember(
    val id: Int = 0,
    val name: String = "",
    val character: String = ""
)

data class CrewMember(
    val id: Int = 0,
    val name: String = "",
    val job: String = ""
)

data class SubdlResponse(
    val status: Boolean = false,
    val subtitles: List<SubdlItem> = emptyList()
)

data class SubdlItem(
    @SerializedName("release_name") val releaseName: String = "",
    val url: String = "",
    val language: String = "",
    @SerializedName("full_name") val fullName: String = "",
    val author: String = "",
    @SerializedName("file_name") val fileName: String = ""
)

data class StreamSource(
    val server: String,
    val quality: String,
    val url: String
)
