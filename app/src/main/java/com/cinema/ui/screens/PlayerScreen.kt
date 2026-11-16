package com.cinema.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cinema.ui.theme.CinemaBackground

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    title: String,
    subtitleUrl: String? = null
) {
    val context = LocalContext.current

    var error by remember {
        mutableStateOf<String?>(null)
    }

    val player = remember(videoUrl) {
        if (videoUrl.isBlank()) {
            null
        } else {
            ExoPlayer.Builder(context).build().apply {
                val item = ExoMediaItem.Builder()
                    .setUri(Uri.parse(videoUrl))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .build()
                    )
                    .build()

                setMediaItem(item)
                prepare()
                playWhenReady = true
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            player?.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            title,
            color = androidx.compose.ui.graphics.Color.White
        )

        if (player == null) {
            Text(
                "A direct video URL is required.",
                color = androidx.compose.ui.graphics.Color.White
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (!subtitleUrl.isNullOrBlank()) {
            Text(
                "Subtitle selected. External subtitle loading will be added in the next player update.",
                color = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}
