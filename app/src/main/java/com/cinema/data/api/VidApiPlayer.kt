package com.cinema.data.api

import android.net.Uri
import com.cinema.data.model.MediaIds

/** Builds vaplayer.ru embed URLs (see VidAPI docs: /embed/movie/{id}, /embed/tv/{id}/{s}/{e}). */
object VidApiPlayer {

    const val HOST = "vaplayer.ru"

    private const val PRIMARY_COLOR = "#8B5CF6"

    fun isValidId(id: String): Boolean =
        MediaIds.imdbOrNull(id) != null || MediaIds.tmdbOrNull(id) != null

    fun isAllowedUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        return uri.scheme == "https" && uri.host == HOST
    }

    fun movieUrl(id: String, title: String?, subtitleLang: String?): String? {
        if (!isValidId(id)) return null

        return base()
            .appendPath("movie")
            .appendPath(id)
            .decorate(title, subtitleLang)
            .build()
            .toString()
    }

    fun episodeUrl(
        id: String,
        season: Int,
        episode: Int,
        title: String?,
        subtitleLang: String?
    ): String? {
        if (!isValidId(id) || season < 1 || episode < 1) return null

        return base()
            .appendPath("tv")
            .appendPath(id)
            .appendPath(season.toString())
            .appendPath(episode.toString())
            .decorate(title, subtitleLang)
            .build()
            .toString()
    }

    private fun base(): Uri.Builder =
        Uri.Builder()
            .scheme("https")
            .authority(HOST)
            .appendPath("embed")

    private fun Uri.Builder.decorate(title: String?, subtitleLang: String?): Uri.Builder {
        appendQueryParameter("primaryColor", PRIMARY_COLOR)
        if (!title.isNullOrBlank()) appendQueryParameter("title", title.take(120))
        if (!subtitleLang.isNullOrBlank()) appendQueryParameter("ds_lang", subtitleLang)
        return this
    }
}
