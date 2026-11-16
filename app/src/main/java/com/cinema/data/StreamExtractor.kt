package com.cinema.data

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class StreamSource(
    val server  : String,
    val quality : String,
    val url     : String
)

// سایت‌هایی که لینک پخش از اونا استخراج می‌شه:
// vidsrc.xyz  — پشتیبانی خوب از HLS
// embed.su    — پشتیبانی خوب از HLS
// vidsrc.to   — محبوب، گاهی JS سنگین
// vidsrc.me   — پشتیبان

private val PROVIDERS = listOf(
    "vidsrc.xyz" to { id: String -> "https://vidsrc.xyz/embed/movie?imdb=$id" },
    "embed.su"   to { id: String -> "https://embed.su/embed/movie/$id"         },
    "vidsrc.to"  to { id: String -> "https://vidsrc.to/embed/movie/$id"        },
    "vidsrc.me"  to { id: String -> "https://vidsrc.me/embed/movie?imdb=$id"   },
)

private val TV_PROVIDERS = listOf(
    "vidsrc.xyz" to { id: String, s: Int, e: Int ->
        "https://vidsrc.xyz/embed/tv?imdb=$id&season=$s&episode=$e" },
    "embed.su"   to { id: String, s: Int, e: Int ->
        "https://embed.su/embed/tv/$id/$s/$e"                       },
    "vidsrc.to"  to { id: String, s: Int, e: Int ->
        "https://vidsrc.to/embed/tv/$id/$s/$e"                      },
    "vidsrc.me"  to { id: String, s: Int, e: Int ->
        "https://vidsrc.me/embed/tv?imdb=$id&season=$s&episode=$e"  },
)

suspend fun extractMovieStreams(
    context   : Context,
    imdbId    : String,
    onProgress: (String) -> Unit = {}
): List<StreamSource> {
    val results = mutableListOf<StreamSource>()
    for ((name, buildUrl) in PROVIDERS) {
        onProgress("Trying $name...")
        val source = tryProvider(context, buildUrl(imdbId), name)
        if (source != null) {
            results.add(source)
            if (results.size >= 2) break
        }
    }
    return results
}

suspend fun extractTvStreams(
    context   : Context,
    imdbId    : String,
    season    : Int,
    episode   : Int,
    onProgress: (String) -> Unit = {}
): List<StreamSource> {
    val results = mutableListOf<StreamSource>()
    for ((name, buildUrl) in TV_PROVIDERS) {
        onProgress("Trying $name...")
        val source = tryProvider(context, buildUrl(imdbId, season, episode), name)
        if (source != null) {
            results.add(source)
            if (results.size >= 2) break
        }
    }
    return results
}

@SuppressLint("SetJavaScriptEnabled")
private suspend fun tryProvider(
    context : Context,
    embedUrl: String,
    name    : String
): StreamSource? = withTimeoutOrNull(22_000L) {
    suspendCancellableCoroutine { cont ->
        var webView: WebView? = null
        var done = false

        fun finish(result: StreamSource?) {
            if (!done) {
                done = true
                webView?.destroy()
                webView = null
                if (cont.isActive) cont.resume(result)
            }
        }

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled               = true
                    domStorageEnabled               = true
                    mixedContentMode                = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    mediaPlaybackRequiresUserGesture = false
                    userAgentString                 =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/125.0.0.0 Mobile Safari/537.36"
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view   : WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        val url = request.url.toString()
                        val stream = detectStream(url) ?: return null
                        val quality = when {
                            "1080" in url  -> "1080p"
                            "720"  in url  -> "720p"
                            "480"  in url  -> "480p"
                            ".m3u8" in url -> "HLS"
                            else           -> "Auto"
                        }
                        finish(StreamSource(name, quality, stream))
                        return null
                    }

                    override fun onReceivedError(
                        view   : WebView,
                        request: WebResourceRequest,
                        error  : WebResourceError
                    ) {
                        if (request.isForMainFrame) finish(null)
                    }
                }

                loadUrl(embedUrl)
            }

            cont.invokeOnCancellation { finish(null) }
        }
    }
}

private fun detectStream(url: String): String? {
    if (!url.startsWith("http")) return null
    val streamExts = listOf(".m3u8", ".mp4", ".mkv", ".webm")
    if (streamExts.none { url.contains(it) }) return null
    val blocked = listOf("ads", "analytics", "tracking", "pixel", "beacon")
    if (blocked.any { url.contains(it) }) return null
    return url
}
