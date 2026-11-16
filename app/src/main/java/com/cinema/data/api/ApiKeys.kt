
package com.cinema.data.api

import com.cinema.BuildConfig

object ApiKeys {

    /**
     * TMDB Bearer token is supplied through a Gradle property
     * or the TMDB_BEARER_TOKEN environment variable.
     */
    val TMDB_BEARER: String
        get() = BuildConfig.TMDB_BEARER_TOKEN.trim()

    /**
     * SubDL key must be entered by the user in the app.
     * Never put a real personal key in source code.
     */
    const val SUBDL: String = ""
}
