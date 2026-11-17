package com.cinema.data.api

import com.cinema.data.model.MovieDetail
import com.cinema.data.model.TmdbSearchResponse
import com.cinema.data.model.TvDetail
import com.cinema.data.model.TvSeasonDetail
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TmdbService {

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query")         query       : String,
        @Query("language")      language    : String  = "en-US",
        @Query("page")          page        : Int     = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbSearchResponse

    @GET("trending/all/week")
    suspend fun trendingAll(
        @Query("language") language: String = "en-US"
    ): TmdbSearchResponse

    @GET("movie/{id}")
    suspend fun movieDetail(
        @Path("id")                  id      : Int,
        @Query("language")           language: String = "en-US",
        @Query("append_to_response") append  : String = "credits"
    ): MovieDetail

    @GET("tv/{id}")
    suspend fun tvDetail(
        @Path("id")                  id      : Int,
        @Query("language")           language: String = "en-US",
        @Query("append_to_response") append  : String = "external_ids"
    ): TvDetail

    @GET("tv/{id}/season/{season_number}")
    suspend fun seasonDetail(
        @Path("id")            id      : Int,
        @Path("season_number") season  : Int,
        @Query("language")     language: String = "en-US"
    ): TvSeasonDetail
}

object TmdbApi {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = ApiKeys.TMDB_BEARER
                val builder = chain.request().newBuilder()
                    .addHeader("accept", "application/json")
                if (token.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            })
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    val service: TmdbService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbService::class.java)
    }
}
