
package com.cinema.data.model

import com.google.gson.annotations.SerializedName

data class VidApiMovie(
    @SerializedName("imdb_id")
    val imdbId: String? = null,

    @SerializedName("tmdb_id")
    val tmdbId: String? = null,

    @SerializedName("title")
    val title: String? = null,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("year")
    val year: String? = null,

    @SerializedName("poster")
    val poster: String? = null,

    @SerializedName("poster_url")
    val posterUrl: String? = null,

    @SerializedName("rating")
    val rating: String? = null,

    @SerializedName("popularity")
    val popularity: String? = null,

    @SerializedName("genre")
    val genre: String? = null,

    @SerializedName("type")
    val type: String? = null,

    @SerializedName("embed_url")
    val embedUrl: String? = null
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: "Unknown title"

    val displayPoster: String?
        get() = posterUrl?.takeIf { it.isNotBlank() }
            ?: poster?.takeIf { it.isNotBlank() }

    val validImdbId: String?
        get() = imdbId?.takeIf {
            Regex("^tt[0-9]{5,12}$").matches(it)
        }
}

data class VidApiCatalogResponse(
    @SerializedName("items")
    val items: List<VidApiMovie> = emptyList(),

    @SerializedName("page")
    val page: Int = 1,

    @SerializedName("per_page")
    val perPage: Int = 24,

    @SerializedName("total")
    val total: Int = 0,

    @SerializedName("total_pages")
    val totalPages: Int = 0
)
