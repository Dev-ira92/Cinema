package com.cinema.data.api

import com.cinema.data.model.SubdlResponse
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SubdlService {

    @GET("api/v1/subtitles")
    suspend fun search(
        @Query("api_key") key: String,
        @Query("imdb_id") imdbId: String? = null,
        @Query("tmdb_id") tmdbId: Int? = null,
        @Query("film_name") filmName: String? = null,
        @Query("file_name") fileName: String? = null,
        @Query("year") year: Int? = null,
        @Query("languages") languages: String = "FA",
        @Query("type") type: String = "movie",
        @Query("season_number") season: Int? = null,
        @Query("episode_number") episode: Int? = null,
        @Query("subs_per_page") perPage: Int = 30,
        @Query("unpack") unpack: Int = 1
    ): SubdlResponse
}

object SubtitleApi {

    const val DOWNLOAD_BASE = "https://dl.subdl.com"

    val service: SubdlService by lazy {

        Retrofit.Builder()
            .baseUrl("https://api.subdl.com/")
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SubdlService::class.java)
    }
}
