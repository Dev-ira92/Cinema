package com.cinema.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.cinema.data.api.VidApiPlayer
import com.cinema.ui.theme.CinemaPrimary
import com.cinema.ui.theme.CinemaRed
import com.cinema.ui.theme.CinemaText

@Composable
fun PlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val safeUrl = remember(url) { url.takeIf { VidApiPlayer.isAllowedUrl(it) } }

    var loading by remember { mutableStateOf(safeUrl != null) }
    var error by remember { mutableStateOf<String?>(null) }

    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Plain holder (not state): only used from lifecycle callbacks.
    val webViewHolder = remember { arrayOfNulls<WebView>(1) }

    fun exitFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        fullscreenView = null
    }

    LaunchedEffect(safeUrl) {
        if (safeUrl == null) {
            loading = false
            error = "Invalid player URL."
        }
    }

    BackHandler(enabled = fullscreenView != null) { exitFullscreen() }

    // Pause the page (and its audio) when the app goes to background.
    DisposableEffect(activity) {
        val owner = activity as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webViewHolder[0]?.onPause()
                Lifecycle.Event.ON_RESUME -> webViewHolder[0]?.onResume()
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    // Landscape + hidden system bars while the page is in fullscreen.
    val isFullscreen = fullscreenView != null
    DisposableEffect(isFullscreen) {
        val act = activity
        if (isFullscreen && act != null) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (isFullscreen && act != null) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CinemaText
                    )
                }
                Text(
                    text = title,
                    color = CinemaText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (safeUrl != null) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setBackgroundColor(0xFF000000.toInt())

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    javaScriptCanOpenWindowsAutomatically = false
                                    setSupportMultipleWindows(false)
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onShowCustomView(
                                        view: View?,
                                        callback: WebChromeClient.CustomViewCallback?
                                    ) {
                                        if (view == null) {
                                            callback?.onCustomViewHidden()
                                            return
                                        }
                                        fullscreenCallback = callback
                                        fullscreenView = view
                                    }

                                    override fun onHideCustomView() {
                                        fullscreenCallback = null
                                        fullscreenView = null
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        loading = true
                                        error = null
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        loading = false
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        err: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            loading = false
                                            error = "Unable to load the player. Check your connection."
                                        }
                                    }

                                    // Never leave https (blocks intent:// and similar redirects).
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean = request?.url?.scheme != "https"
                                }

                                webViewHolder[0] = this
                                loadUrl(safeUrl)
                            }
                        },
                        onRelease = { webView ->
                            webViewHolder[0] = null
                            webView.stopLoading()
                            webView.loadUrl("about:blank")
                            webView.destroy()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (loading && error == null) {
                    CircularProgressIndicator(
                        color = CinemaPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                error?.let { message ->
                    Text(
                        text = message,
                        color = CinemaRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }
            }
        }

        fullscreenView?.let { view ->
            key(view) {
                AndroidView(
                    factory = {
                        (view.parent as? ViewGroup)?.removeView(view)
                        view
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
