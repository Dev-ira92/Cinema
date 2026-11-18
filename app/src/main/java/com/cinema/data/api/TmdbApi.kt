package com.cinema.data.api

import com.cinema.data.model.MediaItem
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Holds the user's TMDB credential: a v3 API key or a v4 "read access token" (starts with eyJ). */
object TmdbConfig {
    @Volatile
    var key: String = ""

    val hasKey: Boolean
        get() = key.isNotBlank()
}

fun tmdbImage(path: String?, size: String): String? =
    path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }

data class TmdbGenre(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null
)

data class TmdbCast(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("character") val character: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null
)

data class TmdbCrew(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("job") val job: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null
)

data class TmdbCredits(
    @SerializedName("cast") val cast: List<TmdbCast>? = null,
    @SerializedName("crew") val crew: List<TmdbCrew>? = null
)

data class TmdbCreator(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null
)

data class TmdbDetails(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("tagline") val tagline: String? = null,
    @SerializedName("genres") val genres: List<TmdbGenre>? = null,
    @SerializedName("runtime") val runtime: Int? = null,
    @SerializedName("episode_run_time") val episodeRunTime: List<Int>? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerializedName("created_by") val createdBy: List<TmdbCreator>? = null,
    @SerializedName("credits") val credits: TmdbCredits? = null
)

data class TmdbEpisode(
    @SerializedName("episode_number") val episodeNumber: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("air_date") val airDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null
)

data class TmdbSeason(
    @SerializedName("episodes") val episodes: List<TmdbEpisode>? = null
)

/** One search/credit row: a movie, a TV show or a person (see mediaType). */
data class TmdbHit(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("popularity") val popularity: Double? = null,
    @SerializedName("known_for_department") val knownForDepartment: String? = null
)

data class TmdbPage(
    @SerializedName("results") val results: List<TmdbHit>? = null
)

data class TmdbFindResponse(
    @SerializedName("movie_results") val movies: List<TmdbHit>? = null,
    @SerializedName("tv_results") val tv: List<TmdbHit>? = null
)

data class TmdbPerson(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("biography") val biography: String? = null,
    @SerializedName("birthday") val birthday: String? = null,
    @SerializedName("place_of_birth") val placeOfBirth: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null,
    @SerializedName("known_for_department") val knownForDepartment: String? = null
)

data class TmdbPersonCredits(
    @SerializedName("cast") val cast: List<TmdbHit>? = null,
    @SerializedName("crew") val crew: List<TmdbHit>? = null
)

interface TmdbService {

    @GET("movie/{id}")
    suspend fun movie(
        @Path("id") id: Int,
        @Query("language") language: String,
        @Query("append_to_response") append: String
    ): TmdbDetails

    @GET("tv/{id}")
    suspend fun tv(
        @Path("id") id: Int,
        @Query("language") language: String,
        @Query("append_to_response") append: String
    ): TmdbDetails

    @GET("tv/{id}/season/{season}")
    suspend fun season(
        @Path("id") id: Int,
        @Path("season") season: Int,
        @Query("language") language: String
    ): TmdbSeason

    @GET("find/{imdbId}")
    suspend fun find(
        @Path("imdbId") imdbId: String,
        @Query("external_source") externalSource: String
    ): TmdbFindResponse

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("language") language: String,
        @Query("page") page: Int,
        @Query("include_adult") includeAdult: Boolean
    ): TmdbPage

    @GET("person/{id}")
    suspend fun person(
        @Path("id") id: Int,
        @Query("language") language: String
    ): TmdbPerson

    @GET("person/{id}/combined_credits")
    suspend fun personCredits(
        @Path("id") id: Int,
        @Query("language") language: String
    ): TmdbPersonCredits
}

object TmdbApi {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val key = TmdbConfig.key
                val original = chain.request()
                val request = if (key.startsWith("eyJ")) {
                    original.newBuilder().header("Authorization", "Bearer $key").build()
                } else {
                    original.newBuilder()
                        .url(original.url.newBuilder().addQueryParameter("api_key", key).build())
                        .build()
                }
                chain.proceed(request)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val service: TmdbService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbService::class.java)
    }
}

data class PersonItem(
    val id: Int,
    val name: String,
    val photoUrl: String?,
    val department: String
)

fun TmdbHit.toPersonItem(): PersonItem? {
    if (mediaType != "person") return null
    val personId = id ?: return null
    return PersonItem(
        id = personId,
        name = name.orEmpty().ifBlank { "Unknown" },
        photoUrl = tmdbImage(profilePath, "w185"),
        department = knownForDepartment.orEmpty()
    )
}

/** Movie/TV hit -> app item (only the TMDB id is known; the player accepts TMDB ids). */
fun TmdbHit.toMediaItem(): MediaItem? {
    val isTv = when (mediaType) {
        "movie" -> false
        "tv" -> true
        else -> return null
    }
    val tmdb = id?.toString() ?: return null
    val date = if (isTv) firstAirDate else releaseDate

    return MediaItem(
        tmdbId = tmdb,
        title = (if (isTv) name else title).orEmpty().ifBlank { "Untitled" },
        year = date.orEmpty().take(4),
        posterUrl = tmdbImage(posterPath, "w342"),
        rating = voteAverage?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f", it) }.orEmpty(),
        isTv = isTv
    )
}
