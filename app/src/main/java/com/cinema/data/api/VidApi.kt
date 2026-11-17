
package com.cinema.data.api

import com.cinema.data.model.VidApiCatalogResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface VidApi {

    @GET("movies/latest/page-{page}.json")
    suspend fun latestMovies(
        @Path("page") page: Int
    ): VidApiCatalogResponse

    @GET("tvshows/latest/page-{page}.json")
    suspend fun latestTvShows(
        @Path("page") page: Int
    ): VidApiCatalogResponse
}
