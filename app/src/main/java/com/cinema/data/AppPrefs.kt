package com.cinema.data

import android.content.Context

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
    private const val KEY_OMDB = "omdb_key"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun subtitleLang(context: Context): String =
        prefs(context).getString(KEY_SUBTITLE_LANG, "fa") ?: "fa"

    fun setSubtitleLang(context: Context, code: String) {
        prefs(context).edit().putString(KEY_SUBTITLE_LANG, code).apply()
    }

    fun omdbKey(context: Context): String =
        prefs(context).getString(KEY_OMDB, "") ?: ""

    fun setOmdbKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_OMDB, key.trim()).apply()
    }
}
