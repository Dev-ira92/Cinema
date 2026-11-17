
package com.cinema.ui.screens

import android.graphics.Color
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import com.cinema.ui.theme.CinemaBackground
import com.cinema.ui.theme.CinemaText

@Composable
fun PlayerScreen(
    videoUrl: String,
    title: String,
    subtitleUrl: String?,
    onBack: () -> Unit
) {
    var loading by remember {
        mutableStateOf(true)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    val embedUrl = remember(videoUrl, subtitleUrl) {
        buildVidApiUrl(videoUrl, subtitleUrl)
    }

    val webView = remember {
        WebView(androidx.compose.ui.platform.LocalContext.current)
    }

    DisposableEffect(webView, embedUrl) {

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }

        webView.setBackgroundColor(Color.BLACK)

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                loading = true
                error = null
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                loading = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                errorInfo: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    loading = false
                    error = "Unable to load VidAPI player. Check your internet connection."
                }
            }
        }

        if (embedUrl != null) {
            webView.tag = embedUrl
            webView.loadUrl(embedUrl)
        } else {
            loading = false
            error = "Invalid VidAPI embed URL."
        }

        onDispose {
            webView.stopLoading()
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }

        Text(
            text = title,
            color = CinemaText,
            style = MaterialTheme.typography.titleLarge
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(androidx.compose.ui.graphics.Color.Black)
        ) {

            if (embedUrl != null) {
                AndroidView(
                    factory = {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        webView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (loading) {
                CircularProgressIndicator()
            }

            error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Text(
            text = "Powered by VidAPI",
            color = CinemaText,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun buildVidApiUrl(
    videoUrl: String,
    subtitleUrl: String?
): String? {

    return try {

        val uri = Uri.parse(videoUrl.trim())

        if (uri.scheme != "https") {
            return null
        }

        if (uri.host != "vaplayer.ru") {
            return null
        }

        val path = uri.path.orEmpty()

        val validPath =
            path.startsWith("/embed/movie/") ||
            path.startsWith("/embed/tv/") ||
            path == "/embed/movie" ||
            path == "/embed/tv"

        if (!validPath) {
            return null
        }

        val builder = uri.buildUpon()

        if (!subtitleUrl.isNullOrBlank()) {
            builder
                .appendQueryParameter("sub_url", subtitleUrl)
                .appendQueryParameter("sub_default", "true")
        }

        builder.build().toString()

    } catch (_: Exception) {
        null
    }
}
