package com.cinema.data

import android.content.Context
import com.cinema.BuildConfig
import com.cinema.data.api.TmdbConfig

object AppPrefs {

    data class SubtitleLanguage(val code: String, val label: String)

    /** Empty code = do not ask the player for subtitles. */
    val subtitleLanguages = listOf(
        SubtitleLanguage("fa", "Persian"),
        SubtitleLanguage("en", "English"),
        SubtitleLanguage("ar", "Arabic"),
        SubtitleLanguage("tr", "Turkish"),
        SubtitleLanguage("", "Off")
    )

    private const val FILE = "cinema_prefs"
    private const val KEY_SUBTITLE_LANG = "subtitle_lang"
    private const val KEY_TMDB = "tmdb_key"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun subtitleLang(context: Context): String =
        prefs(context).getString(KEY_SUBTITLE_LANG, "fa") ?: "fa"

    fun setSubtitleLang(context: Context, code: String) {
        prefs(context).edit().putString(KEY_SUBTITLE_LANG, code).apply()
    }

    /** User-entered key wins; otherwise the build-time default is used. */
    fun tmdbKey(context: Context): String =
        prefs(context).getString(KEY_TMDB, "")?.takeIf { it.isNotBlank() }
            ?: BuildConfig.TMDB_DEFAULT_KEY

    fun setTmdbKey(context: Context, key: String) {
        val clean = key.trim()
        prefs(context).edit().putString(KEY_TMDB, clean).apply()
        TmdbConfig.key = clean
    }
}
