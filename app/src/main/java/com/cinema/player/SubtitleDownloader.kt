
package com.cinema.player

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

object SubtitleDownloader {

    private val client = OkHttpClient()

    suspend fun download(
        context: Context,
        subtitleUrl: String
    ): File = withContext(Dispatchers.IO) {

        require(
            subtitleUrl.startsWith("https://") ||
            subtitleUrl.startsWith("http://")
        ) {
            "Invalid subtitle URL"
        }

        val request = Request.Builder()
            .url(subtitleUrl)
            .get()
            .build()

        client.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                throw IOException(
                    "Subtitle download failed: ${response.code}"
                )
            }

            val body = response.body
                ?: throw IOException("Empty subtitle response")

            val file = File(
                context.cacheDir,
                "cinema_subtitle_${System.currentTimeMillis()}.srt"
            )

            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (file.length() == 0L) {
                file.delete()
                throw IOException("Downloaded subtitle is empty")
            }

            file
        }
    }
}
