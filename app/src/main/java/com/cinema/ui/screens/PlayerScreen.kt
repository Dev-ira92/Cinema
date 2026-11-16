
package com.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cinema.data.SubtitleDownloader
import com.cinema.player.SubtitlePlayer
import com.cinema.ui.theme.CinemaBackground
import com.cinema.ui.theme.CinemaText
import kotlinx.coroutines.CancellationException

@Composable
fun PlayerScreen(
    videoUrl: String,
    title: String,
    subtitleUrl: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var loading by remember {
        mutableStateOf(false)
    }

    var buffering by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var playbackState by remember {
        mutableIntStateOf(Player.STATE_IDLE)
    }

    val player = remember(context) {
        ExoPlayer.Builder(context.applicationContext)
            .build()
    }

    // Listen to actual player state and playback errors.
    DisposableEffect(player) {

        val listener = object : Player.Listener {

            override fun onPlaybackStateChanged(
                state: Int
            ) {
                playbackState = state
                buffering = state == Player.STATE_BUFFERING
            }

            override fun onPlayerError(
                playbackException: PlaybackException
            ) {
                error = when (
                    playbackException.errorCode
                ) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Network connection failed. Check your internet connection."

                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "The video server rejected the request."

                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                        "The video format or stream is invalid."

                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED ->
                        "This video format could not be decoded on this device."

                    else ->
                        playbackException.message
                            ?: "Video playback failed."
                }
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Download subtitle if selected, then prepare the video.
    LaunchedEffect(
        videoUrl,
        subtitleUrl
    ) {

        error = null
        loading = true
        buffering = false
        playbackState = Player.STATE_IDLE

        try {

            if (videoUrl.isBlank()) {
                throw IllegalArgumentException(
                    "Video URL is empty."
                )
            }

            val cleanVideoUrl = videoUrl.trim()

            val mediaItem = if (
                !subtitleUrl.isNullOrBlank()
            ) {

                val subtitleFile = SubtitleDownloader.download(
                    context = context,
                    subtitleUrl = subtitleUrl
                )

                if (
                    !subtitleFile.exists() ||
                    subtitleFile.length() == 0L
                ) {
                    throw IllegalStateException(
                        "The downloaded subtitle file is empty."
                    )
                }

                SubtitlePlayer.createMediaItem(
                    videoUrl = cleanVideoUrl,
                    subtitleUrl = android.net.Uri
                        .fromFile(subtitleFile)
                        .toString()
                )

            } else {

                SubtitlePlayer.createMediaItem(
                    videoUrl = cleanVideoUrl,
                    subtitleUrl = null
                )
            }

            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true

        } catch (e: CancellationException) {

            throw e

        } catch (e: Exception) {

            error = e.message
                ?: "Unable to prepare video."

        } finally {

            loading = false
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

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { viewContext ->

                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    keepScreenOn = true
                }
            },
            update = { view ->
                if (view.player !== player) {
                    view.player = player
                }
            }
        )

        if (loading) {

            CircularProgressIndicator()

            Text(
                text = "Downloading subtitle and preparing video...",
                color = CinemaText
            )
        }

        if (buffering && !loading) {

            Text(
                text = "Buffering...",
                color = CinemaText
            )
        }

        if (
            playbackState == Player.STATE_ENDED &&
            error == null
        ) {
            Text(
                text = "Playback finished.",
                color = CinemaText
            )
        }

        error?.let { message ->

            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = {
                    error = null
                    player.prepare()
                    player.play()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Retry playback")
            }
        }

        Spacer(
            modifier = Modifier.height(4.dp)
        )
    }
}
