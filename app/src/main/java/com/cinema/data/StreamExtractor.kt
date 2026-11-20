package com.cinema.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class ExtractedStream(
    val url    : String,
    val quality: String
)

@SuppressLint("SetJavaScriptEnabled")
suspend fun extractStreamFromEmbed(
    context  : Context,
    embedUrl : String,
    onStatus : (String) -> Unit = {}
): ExtractedStream? = withTimeoutOrNull(30_000L) {

    suspendCancellableCoroutine { cont ->
        var webView: WebView? = null
        var done = false

        fun finish(result: ExtractedStream?) {
            if (done) return
            done = true
            webView?.stopLoading()
            webView?.destroy()
            webView = null
            if (cont.isActive) cont.resume(result)
        }

        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onStatus("Loading player page...")

            val wv = WebView(context)
            webView = wv

            wv.settings.apply {
                javaScriptEnabled                = true
                domStorageEnabled                = true
                mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
                userAgentString                  = userAgentString.replace("; wv", "")
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

            wv.webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view   : WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val url    = request.url.toString()
                    val stream = detectStream(url) ?: return null
                    onStatus("Stream found!")
                    val quality = when {
                        "1080" in url  -> "1080p"
                        "720"  in url  -> "720p"
                        "480"  in url  -> "480p"
                        ".m3u8" in url -> "HLS"
                        else           -> "Auto"
                    }
                    finish(ExtractedStream(stream, quality))
                    return null
                }

                override fun onPageStarted(
                    view: WebView, url: String?, favicon: Bitmap?
                ) { onStatus("Opening player page...") }

                override fun onReceivedError(
                    view   : WebView,
                    request: WebResourceRequest,
                    error  : WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        onStatus("Page load error.")
                        finish(null)
                    }
                }
            }

            wv.loadUrl(embedUrl)
            cont.invokeOnCancellation { finish(null) }
        }
    }
}

private fun detectStream(url: String): String? {
    if (!url.startsWith("http")) return null
    val exts    = listOf(".m3u8", ".mp4", ".mkv")
    val blocked = listOf("ads", "analytics", "tracker", "pixel", "beacon", "stat")
    if (exts.none   { url.contains(it, ignoreCase = true) }) return null
    if (blocked.any { url.contains(it, ignoreCase = true) }) return null
    return url
}
