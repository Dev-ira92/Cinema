
package com.cinema.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

@UnstableApi
object SubtitlePlayer {

    fun createMediaItem(
        videoUrl: String,
        subtitleUrl: String?
    ): MediaItem {

        val builder = MediaItem.Builder()
            .setUri(Uri.parse(videoUrl))

        if (!subtitleUrl.isNullOrBlank()) {

            val subtitle = MediaItem.SubtitleConfiguration.Builder(
                Uri.parse(subtitleUrl)
            )
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("fa")
                .setSelectionFlags(
                    MediaItem.SelectionFlags.DEFAULT
                )
                .build()

            builder.setSubtitleConfigurations(
                listOf(subtitle)
            )
        }

        return builder.build()
    }

    fun preparePlayer(
        player: ExoPlayer,
        videoUrl: String,
        subtitleUrl: String?
    ) {

        val mediaItem = createMediaItem(
            videoUrl = videoUrl,
            subtitleUrl = subtitleUrl
        )

        player.setMediaItem(mediaItem)
        player.prepare()
    }
}
