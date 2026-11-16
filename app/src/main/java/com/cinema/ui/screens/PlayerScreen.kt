
package com.cinema.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cinema.data.SubtitleDownloader
import com.cinema.player.SubtitlePlayer
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

        if (videoUrl.isBlank()) {
            error = "آدرس ویدئو معتبر نیست"
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

            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "پخش ویدئو ناموفق بود"
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("بازگشت")
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                }
            },
            update = { view ->
                view.player = player
            }
        )

        if (loading) {
            CircularProgressIndicator()
            Text("در حال آماده‌سازی ویدئو و زیرنویس...")
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
