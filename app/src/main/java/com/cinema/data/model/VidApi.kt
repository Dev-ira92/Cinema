package com.cinema.data.api

import com.cinema.data.model.VidApiEpisodeResponse
import com.cinema.data.model.VidApiMovieResponse
import com.cinema.data.model.VidApiStatsResponse
import com.cinema.data.model.VidApiTvResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface VidApiService {

    @GET("movies/latest/page-{page}.json")
    suspend fun latestMovies(
        @Path("page") page: Int
    ): VidApiMovieResponse

    @GET("tvshows/latest/page-{page}.json")
    suspend fun latestTvShows(
        @Path("page") page: Int
    ): VidApiTvResponse

    @GET("episodes/latest/page-{page}.json")
    suspend fun latestEpisodes(
        @Path("page") page: Int
    ): VidApiEpisodeResponse

    @GET("imdb/api/")
    suspend fun getStats(
        @Query("action") action: String = "stats"
    ): VidApiStatsResponse
}

object VidApi {

    private const val BASE_URL = "https://vidapi.ru/"

    private const val PLAYER_BASE_URL = "https://vaplayer.ru/"

    private val client: OkHttpClient by lazy {

        OkHttpClient.Builder()

            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.NONE
                }
            )

            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)

            .build()
    }

    val service: VidApiService by lazy {

        Retrofit.Builder()

            .baseUrl(BASE_URL)

            .client(client)

            .addConverterFactory(
                GsonConverterFactory.create()
            )

            .build()

            .create(VidApiService::class.java)
    }

    fun movieEmbedUrl(imdbId: String): String {

        require(isValidImdbId(imdbId)) {
            "Invalid IMDb ID"
        }

        return "${PLAYER_BASE_URL}embed/movie/$imdbId"
    }

    fun tvEmbedUrl(
        imdbId: String,
        season: Int,
        episode: Int
    ): String {

        require(isValidImdbId(imdbId)) {
            "Invalid IMDb ID"
        }

        require(season > 0) {
            "Invalid season number"
        }

        require(episode > 0) {
            "Invalid episode number"
        }

        return "${PLAYER_BASE_URL}embed/tv/$imdbId/$season/$episode"
    }

    private fun isValidImdbId(id: String): Boolean {
        return Regex("^tt[0-9]+$").matches(id)
    }
}
