package com.cinema.data.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** OMDb returns "N/A" for missing values; this turns them into null. */
fun String?.omdbClean(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) }

data class OmdbDetails(
    @SerializedName("Title") val title: String? = null,
    @SerializedName("Year") val year: String? = null,
    @SerializedName("Rated") val rated: String? = null,
    @SerializedName("Released") val released: String? = null,
    @SerializedName("Runtime") val runtime: String? = null,
    @SerializedName("Genre") val genre: String? = null,
    @SerializedName("Director") val director: String? = null,
    @SerializedName("Writer") val writer: String? = null,
    @SerializedName("Actors") val actors: String? = null,
    @SerializedName("Plot") val plot: String? = null,
    @SerializedName("Language") val language: String? = null,
    @SerializedName("Country") val country: String? = null,
    @SerializedName("Awards") val awards: String? = null,
    @SerializedName("imdbRating") val imdbRating: String? = null,
    @SerializedName("imdbVotes") val imdbVotes: String? = null,
    @SerializedName("totalSeasons") val totalSeasons: String? = null,
    @SerializedName("Response") val response: String? = null,
    @SerializedName("Error") val error: String? = null
)

data class OmdbEpisode(
    @SerializedName("Title") val title: String? = null,
    @SerializedName("Released") val released: String? = null,
    @SerializedName("Episode") val episode: String? = null,
    @SerializedName("imdbRating") val imdbRating: String? = null
)

data class OmdbSeason(
    @SerializedName("Season") val season: String? = null,
    @SerializedName("Episodes") val episodes: List<OmdbEpisode>? = null,
    @SerializedName("Response") val response: String? = null,
    @SerializedName("Error") val error: String? = null
)

interface OmdbService {

    @GET("/")
    suspend fun details(
        @Query("i") imdbId: String,
        @Query("plot") plot: String,
        @Query("apikey") apiKey: String
    ): OmdbDetails

    @GET("/")
    suspend fun season(
        @Query("i") imdbId: String,
        @Query("Season") season: Int,
        @Query("apikey") apiKey: String
    ): OmdbSeason
}

/** https://www.omdbapi.com/ - needs a free API key (entered in the app). */
object OmdbApi {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val service: OmdbService by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.omdbapi.com/")
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OmdbService::class.java)
    }
}
