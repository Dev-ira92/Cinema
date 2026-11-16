
package com.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cinema.data.SubtitleDownloader
import com.cinema.player.SubtitlePlayer
import com.cinema.ui.theme.*
import kotlinx.coroutines.CancellationException

@Composable
fun PlayerScreen(
    videoUrl: String,
    title: String,
    subtitleUrl: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val player = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(videoUrl, subtitleUrl) {
        if (!isValidVideoUrl(videoUrl)) {
            error = "Invalid video URL."
            return@LaunchedEffect
        }

        loading = true
        error = null

        try {
            val mediaItem = if (!subtitleUrl.isNullOrBlank()) {
                val subtitleFile = SubtitleDownloader.download(
                    context = context,
                    subtitleUrl = subtitleUrl
                )

                SubtitlePlayer.createMediaItem(
                    videoUrl = videoUrl,
                    subtitleUrl = subtitleFile.toURI().toString()
                )
            } else {
                MediaItem.fromUri(videoUrl)
            }

            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true

        } catch (e: CancellationException) {
            throw e

        } catch (e: Exception) {
            error = e.message ?: "Unable to prepare video playback."
            player.stop()

        } finally {
            loading = false
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("Back")
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = title,
                color = CinemaText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2
            )
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                }
            },
            update = { view ->
                view.player = player
            }
        )

        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = CinemaPrimary,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )

                Spacer(Modifier.width(12.dp))

                Text(
                    text = "Preparing video and subtitles...",
                    color = CinemaMuted
                )
            }
        }

        if (!error.isNullOrBlank()) {
            Text(
                text = error.orEmpty(),
                color = CinemaRed,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun isValidVideoUrl(value: String): Boolean {
    return value.startsWith("https://", ignoreCase = true) ||
        value.startsWith("http://", ignoreCase = true)
}
