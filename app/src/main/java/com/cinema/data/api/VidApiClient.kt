
package com.cinema.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object VidApiClient {

    private const val BASE_URL = "https://vidapi.ru/"

    val service: VidApi by lazy {

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(VidApi::class.java)
    }
}
