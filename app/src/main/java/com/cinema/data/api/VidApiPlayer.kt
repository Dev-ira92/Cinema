
package com.cinema.data.api

import android.net.Uri

object VidApiPlayer {

    private const val PLAYER_HOST = "vaplayer.ru"

    fun movieUrl(imdbId: String): String? {

        if (!isValidImdbId(imdbId)) return null

        return "https://$PLAYER_HOST/embed/movie/$imdbId"
    }

    fun episodeUrl(
        imdbId: String,
        season: Int,
        episode: Int
    ): String? {

        if (!isValidImdbId(imdbId)) return null
        if (season < 1 || episode < 1) return null

        return "https://$PLAYER_HOST/embed/tv/$imdbId/$season/$episode"
    }

    fun addSubtitle(
        playerUrl: String,
        subtitleUrl: String
    ): String? {

        return try {

            val uri = Uri.parse(playerUrl)

            if (
                uri.scheme != "https" ||
                uri.host != PLAYER_HOST
            ) {
                return null
            }

            if (!subtitleUrl.startsWith("https://")) {
                return null
            }

            uri.buildUpon()
                .appendQueryParameter(
                    "sub_url",
                    subtitleUrl
                )
                .build()
                .toString()

        } catch (_: Exception) {
            null
        }
    }

    private fun isValidImdbId(value: String): Boolean {
        return Regex("^tt[0-9]{5,12}$").matches(value)
    }
}
