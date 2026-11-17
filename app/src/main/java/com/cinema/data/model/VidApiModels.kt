package com.cinema.data.model

import com.google.gson.annotations.SerializedName

data class VidApiItem(
    @SerializedName("tmdb_id") val tmdbId: String? = null,
    @SerializedName("imdb_id") val imdbId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("poster_url") val posterUrl: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("embed_url") val embedUrl: String? = null
)

data class VidApiPage(
    @SerializedName("page") val page: Int = 1,
    @SerializedName("per_page") val perPage: Int = 24,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("total_pages") val totalPages: Int = 0,
    @SerializedName("items") val items: List<VidApiItem>? = null
)

/** Returns null when the item has neither a valid IMDb nor TMDB id (it could not be played). */
fun VidApiItem.toMediaItem(isTv: Boolean): MediaItem? {
    val imdb = MediaIds.imdbOrNull(imdbId)
    val tmdb = MediaIds.tmdbOrNull(tmdbId)
    if (imdb == null && tmdb == null) return null

    return MediaItem(
        imdbId = imdb,
        tmdbId = tmdb,
        title = title.orEmpty().trim().ifBlank { "Untitled" },
        year = year.orEmpty().trim(),
        // The catalog returns the huge "original" size; a small size is enough for cards.
        posterUrl = posterUrl?.trim()
            ?.takeIf { it.startsWith("https://") }
            ?.replace("/t/p/original/", "/t/p/w342/"),
        rating = rating.orEmpty().trim(),
        genre = genre.orEmpty().trim(),
        isTv = isTv
    )
}
