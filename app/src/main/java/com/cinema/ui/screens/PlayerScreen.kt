
package com.cinema.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cinema.data.SubtitleDownloader
import com.cinema.player.SubtitlePlayer
import kotlinx.coroutines.CancellationException

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    title: String,
    subtitleUrl: String,
    onBack: () -> Unit
) {

    val context = LocalContext.current

    var loadingSubtitle by remember {
        mutableStateOf(false)
    }

    var subtitleMessage by remember {
        mutableStateOf("")
    }

    var playerError by remember {
        mutableStateOf("")
    }

    // Create ExoPlayer

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    // Release player when screen is destroyed

    DisposableEffect(exoPlayer) {

        onDispose {
            exoPlayer.release()
        }
    }

    // Load video and optional subtitle

    LaunchedEffect(
        videoUrl,
        subtitleUrl,
        exoPlayer
    ) {

        playerError = ""
        subtitleMessage = ""
        loadingSubtitle = false

        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        if (videoUrl.isBlank()) {

            playerError = "آدرس ویدئو وارد نشده است."
            return@LaunchedEffect
        }

        val validVideoUrl =
            videoUrl.startsWith("https://", true) ||
            videoUrl.startsWith("http://", true)

        if (!validVideoUrl) {

            playerError = "آدرس ویدئو معتبر نیست."
            return@LaunchedEffect
        }

        try {

            var downloadedSubtitleUrl: String? = null

            // Download subtitle if selected

            if (subtitleUrl.isNotBlank()) {

                loadingSubtitle = true

                try {

                    val subtitleFile =
                        SubtitleDownloader.download(
                            context = context,
                            subtitleUrl = subtitleUrl
                        )

                    downloadedSubtitleUrl =
                        Uri.fromFile(subtitleFile).toString()

                    subtitleMessage =
                        "زیرنویس با موفقیت دریافت شد."

                } catch (e: CancellationException) {

                    throw e

                } catch (e: Exception) {

                    subtitleMessage =
                        "دریافت زیرنویس ناموفق بود: ${e.message}"
                }

                loadingSubtitle = false
            }

            // Prepare media item

            val mediaItem = if (
                downloadedSubtitleUrl != null
            ) {

                SubtitlePlayer.createMediaItem(
                    videoUrl = videoUrl,
                    subtitleUrl = downloadedSubtitleUrl
                )

            } else {

                MediaItem.fromUri(
                    Uri.parse(videoUrl)
                )
            }

            // Start playback

            exoPlayer.setMediaItem(mediaItem)

            exoPlayer.prepare()

            exoPlayer.playWhenReady = true

        } catch (e: CancellationException) {

            exoPlayer.stop()

            throw e

        } catch (e: Exception) {

            playerError =
                "خطا در آماده‌سازی ویدئو: ${e.message}"

        } finally {

            loadingSubtitle = false
        }
    }

    // Screen UI

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // Top bar

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151515))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            TextButton(
                onClick = onBack
            ) {

                Text(
                    text = "بازگشت",
                    color = Color.White
                )
            }

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Video area

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {

            if (playerError.isNotBlank()) {

                Text(
                    text = playerError,
                    color = Color.White,
                    modifier = Modifier.padding(20.dp)
                )

            } else {

                AndroidView(

                    modifier = Modifier.fillMaxSize(),

                    factory = { viewContext ->

                        PlayerView(viewContext).apply {

                            this.player = exoPlayer

                            useController = true
                        }
                    },

                    update = { playerView ->

                        playerView.player = exoPlayer
                    }
                )
            }

            // Subtitle loading indicator

            if (loadingSubtitle) {

                CircularProgressIndicator(
                    modifier = Modifier.align(
                        Alignment.Center
                    ),
                    color = Color.White
                )
            }
        }

        // Subtitle status message

        if (subtitleMessage.isNotBlank()) {

            Text(
                text = subtitleMessage,
                color = Color.LightGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF151515))
                    .padding(12.dp)
            )
        }
    }
}
