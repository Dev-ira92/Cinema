
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

    val player = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(player) {

        onDispose {
            player.release()
        }
    }

    LaunchedEffect(videoUrl, subtitleUrl, player) {

        playerError = ""
        subtitleMessage = ""

        player.stop()
        player.clearMediaItems()

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

        var downloadedSubtitleUrl: String? = null

        try {

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

                    subtitleMessage = "زیرنویس با موفقیت دریافت شد."

                } catch (e: CancellationException) {

                    throw e

                } catch (e: Exception) {

                    subtitleMessage =
                        "دریافت زیرنویس ناموفق بود: ${e.message}"
                }

                loadingSubtitle = false
            }

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

            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true

        } catch (e: CancellationException) {

            player.stop()
            throw e

        } catch (e: Exception) {

            playerError =
                "خطا در آماده‌سازی ویدئو: ${e.message}"

        } finally {

            loadingSubtitle = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // Header

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

        // Video player

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

                            player = this@PlayerScreen.run {
                                this@run.player
                            }

                            useController = true
                        }
                    },

                    update = { playerView ->

                        playerView.player = player
                    }
                )
            }

            if (loadingSubtitle) {

                CircularProgressIndicator(
                    modifier = Modifier.align(
                        Alignment.Center
                    ),
                    color = Color.White
                )
            }
        }

        // Subtitle status

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
