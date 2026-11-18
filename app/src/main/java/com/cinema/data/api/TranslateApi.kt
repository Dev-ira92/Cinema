package com.cinema.data.api

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Translation through the unofficial Google Translate web endpoint (no key).
 * It is undocumented and may be rate-limited or blocked, so callers must handle failures.
 */
object TranslateApi {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun toPersian(text: String): String = withContext(Dispatchers.IO) {
        splitIntoChunks(text, 800).joinToString(" ") { translateChunk(it) }
    }

    private fun translateChunk(query: String): String {
        val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl()
            .newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", "fa")
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", query)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Cinema/1.0")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Translate HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty translate response")

            // Shape: [[["translated","original",...], ...], null, "en", ...]
            val segments = JsonParser.parseString(body).asJsonArray[0].asJsonArray
            return buildString {
                segments.forEach { segment ->
                    val piece = segment.asJsonArray[0]
                    if (!piece.isJsonNull) append(piece.asString)
                }
            }.trim()
        }
    }

    private fun splitIntoChunks(text: String, maxLength: Int): List<String> {
        val sentences = text.trim().split(Regex("(?<=[.!?])\\s+"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (sentence in sentences) {
            if (current.isNotEmpty() && current.length + sentence.length + 1 > maxLength) {
                chunks += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        if (current.isNotEmpty()) chunks += current.toString()

        return chunks.ifEmpty { listOf(text) }
    }
}
