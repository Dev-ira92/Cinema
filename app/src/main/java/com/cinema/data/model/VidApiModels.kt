package com.cinema.data.model

import com.google.gson.annotations.SerializedName

data class VidApiMovieResponse(
    val page: Int = 1,

    @SerializedName("per_page")
    val perPage: Int = 24,

    val total: Int = 0,

    @SerializedName("total_pages")
    val totalPages: Int = 0,

    val items: List<VidApiMovieItem> = emptyList()
)

data class VidApiMovieItem(
    @SerializedName("tmdb_id")
    val tmdbId: String? = null,

    @SerializedName("imdb_id")
    val imdbId: String? = null,

    val title: String = "",

    val year: String = "",

    @SerializedName("poster_url")
    val posterUrl: String? = null,

    val rating: String = "",

    val genre: String = "",

    val popularity: String = "",

    val type: String = "movie",

    @SerializedName("embed_url")
    val embedUrl: String? = null
) {
    val displayTitle: String
        get() = title.ifBlank { "Untitled" }

    val ratingText: String
        get() = rating.toDoubleOrNull()
            ?.takeIf { it > 0 }
            ?.let { "%.1f".format(it) }
            ?: "–"

    val normalizedYear: String
        get() = year.take(4)
}

data class VidApiTvResponse(
    val page: Int = 1,

    @SerializedName("per_page")
    val perPage: Int = 24,

    val total: Int = 0,

    @SerializedName("total_pages")
    val totalPages: Int = 0,

    val items: List<VidApiTvItem> = emptyList()
)

data class VidApiTvItem(
    @SerializedName("tmdb_id")
    val tmdbId: String? = null,

    @SerializedName("imdb_id")
    val imdbId: String? = null,

    val title: String = "",

    val year: String = "",

    @SerializedName("poster_url")
    val posterUrl: String? = null,

    val rating: String = "",

    val genre: String = "",

    val popularity: String = "",

    val type: String = "tv",

    @SerializedName("embed_url")
    val embedUrl: String? = null
) {
    val displayTitle: String
        get() = title.ifBlank { "Untitled" }

    val ratingText: String
        get() = rating.toDoubleOrNull()
            ?.takeIf { it > 0 }
            ?.let { "%.1f".format(it) }
            ?: "–"

    val normalizedYear: String
        get() = year.take(4)
}

data class VidApiEpisodeResponse(
    val page: Int = 1,

    @SerializedName("per_page")
    val perPage: Int = 24,

    val total: Int = 0,

    @SerializedName("total_pages")
    val totalPages: Int = 0,

    val items: List<VidApiEpisodeItem> = emptyList()
)

data class VidApiEpisodeItem(
    @SerializedName("show_tmdb_id")
    val showTmdbId: String? = null,

    @SerializedName("season_number")
    val seasonNumber: String = "",

    @SerializedName("episode_number")
    val episodeNumber: String = "",

    @SerializedName("episode_title")
    val episodeTitle: String = "",

    @SerializedName("air_date")
    val airDate: String = "",

    @SerializedName("show_title")
    val showTitle: String = "",

    @SerializedName("show_imdb_id")
    val showImdbId: String? = null,

    val type: String = "episode",

    @SerializedName("embed_url")
    val embedUrl: String? = null
)

data class VidApiStatsResponse(
    val imdb: VidApiImdbStats? = null,

    @SerializedName("content_library")
    val contentLibrary: VidApiContentStats? = null,

    val cached: Boolean = false,

    @SerializedName("generated_at")
    val generatedAt: String? = null
)

data class VidApiImdbStats(
    @SerializedName("total_titles")
    val totalTitles: Long = 0,

    val movies: Long = 0,

    @SerializedName("tv_series")
    val tvSeries: Long = 0,

    val episodes: Long = 0,

    val people: Long = 0,

    val ratings: Long = 0
)

data class VidApiContentStats(
    val movies: Long = 0,

    @SerializedName("tv_shows")
    val tvShows: Long = 0,

    val episodes: Long = 0,

    val people: Long = 0,

    val collections: Long = 0
)
