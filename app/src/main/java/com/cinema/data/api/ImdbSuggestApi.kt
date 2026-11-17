package com.cinema.data.api

import com.cinema.data.model.MediaIds
import com.cinema.data.model.MediaItem
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/**
 * Unofficial, undocumented IMDb autocomplete endpoint (no API key).
 * It can change or disappear without notice, so every caller must handle failures.
 */
interface ImdbSuggestService {

    @GET("suggestion/{letter}/{query}.json")
    suspend fun suggest(
        @Path("letter") letter: String,
        @Path("query") query: String
    ): ImdbSuggestResponse
}

data class ImdbSuggestResponse(
    @SerializedName("d") val hits: List<ImdbSuggestHit>? = null
)

data class ImdbSuggestHit(
    @SerializedName("id") val id: String? = null,
    @SerializedName("l") val title: String? = null,
    @SerializedName("qid") val kind: String? = null,
    @SerializedName("y") val year: Int? = null,
    @SerializedName("i") val image: ImdbSuggestImage? = null
)

data class ImdbSuggestImage(
    @SerializedName("imageUrl") val imageUrl: String? = null
)

object ImdbSuggestApi {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Cinema/1.0")
                        .build()
                )
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val service: ImdbSuggestService by lazy {
        Retrofit.Builder()
            .baseUrl("https://v3.sg.media-imdb.com/")
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ImdbSuggestService::class.java)
    }

    suspend fun search(rawQuery: String): List<MediaItem> {
        val query = rawQuery.trim().lowercase().take(60)
        if (query.length < 2) return emptyList()

        val first = query.first()
        val letter = if (first in 'a'..'z' || first in '0'..'9') first.toString() else "x"

        return service.suggest(letter, query)
            .hits.orEmpty()
            .mapNotNull { it.toMediaItem() }
            .distinctBy { it.key }
    }

    private fun ImdbSuggestHit.toMediaItem(): MediaItem? {
        val imdb = MediaIds.imdbOrNull(id) ?: return null

        val isTv = when (kind) {
            "movie", "tvMovie" -> false
            "tvSeries", "tvMiniSeries" -> true
            else -> return null // people, episodes, games, shorts...
        }

        return MediaItem(
            imdbId = imdb,
            title = title.orEmpty().ifBlank { "Untitled" },
            year = year?.toString().orEmpty(),
            posterUrl = image?.imageUrl?.takeIf { it.startsWith("https://") },
            isTv = isTv
        )
    }
}
