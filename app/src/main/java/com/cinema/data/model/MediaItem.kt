package com.cinema.data.model

/** Single title shown in the app. Built from VidAPI catalog items or IMDb search hits. */
data class MediaItem(
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val title: String = "",
    val year: String = "",
    val posterUrl: String? = null,
    val rating: String = "",
    val genre: String = "",
    val isTv: Boolean = false
) {
    /** ID accepted by the player: IMDb (tt...) preferred, otherwise numeric TMDB. */
    val embedId: String?
        get() = imdbId ?: tmdbId

    val key: String
        get() = (if (isTv) "tv:" else "movie:") + (embedId ?: title)

    val subtitleLine: String
        get() = listOf(year, if (rating.isNotBlank()) "★ $rating" else "")
            .filter { it.isNotBlank() }
            .joinToString("  ·  ")
}

object MediaIds {
    private val IMDB = Regex("^tt[0-9]{5,12}$")
    private val TMDB = Regex("^[0-9]{1,10}$")

    fun imdbOrNull(value: String?): String? =
        value?.trim()?.takeIf { IMDB.matches(it) }

    fun tmdbOrNull(value: String?): String? =
        value?.trim()?.takeIf { TMDB.matches(it) }
}
