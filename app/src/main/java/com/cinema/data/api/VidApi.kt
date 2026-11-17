package com.cinema.data.api

import com.cinema.data.model.VidApiPage
import retrofit2.http.GET
import retrofit2.http.Path

interface VidApi {

    @GET("movies/latest/page-{page}.json")
    suspend fun latestMovies(@Path("page") page: Int): VidApiPage

    @GET("tvshows/latest/page-{page}.json")
    suspend fun latestTvShows(@Path("page") page: Int): VidApiPage
}
