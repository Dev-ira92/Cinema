
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
import com.cinema.player.SubtitleDownloader
import com.cinema.player.SubtitlePlayer
import kotlinx.coroutines.CancellationException
import java.io.File

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

    var subtitleFile by remember {
        mutableStateOf<File?>(null)
    }

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(videoUrl, subtitleUrl) {

        playerError = ""
        subtitleMessage = ""
        subtitleFile = null

        if (videoUrl.isBlank()) {
            playerError = "آدرس ویدئو وارد نشده است."
            return@LaunchedEffect
        }

        if (!videoUrl.startsWith("https://") &&
            !videoUrl.startsWith("http://")
        ) {
            playerError = "آدرس ویدئو معتبر نیست."
            return@LaunchedEffect
        }

        loadingSubtitle = subtitleUrl.isNotBlank()

        try {

            var downloadedSubtitle: File? = null

            if (subtitleUrl.isNotBlank()) {

                try {

                    downloadedSubtitle =
                        SubtitleDownloader.download(
                            context = context,
                            subtitleUrl = subtitleUrl
                        )

                    subtitleFile = downloadedSubtitle

                    subtitleMessage = "زیرنویس آماده شد."

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {

                    subtitleMessage =
                        "دانلود زیرنویس ناموفق بود: ${e.message}"
                }
            }

            val mediaItem = if (downloadedSubtitle != null) {

                SubtitlePlayer.createMediaItem(
                    videoUrl = videoUrl,
                    subtitleUrl = Uri.fromFile(
                        downloadedSubtitle
                    ).toString()
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
                            this.player = player
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
