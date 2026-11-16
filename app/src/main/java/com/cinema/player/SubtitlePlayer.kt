package com.cinema.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes

object SubtitlePlayer {

    fun createMediaItem(
        videoUrl: String,
        subtitleUrl: String?
    ): MediaItem {

        require(videoUrl.isNotBlank()) {
            "Video URL is empty"
        }

        val videoUri = Uri.parse(videoUrl.trim())

        require(
            videoUri.scheme == "http" ||
            videoUri.scheme == "https"
        ) {
            "Unsupported video URL"
        }

        val builder = MediaItem.Builder()
            .setUri(videoUri)

        if (!subtitleUrl.isNullOrBlank()) {

            val subtitleUri = Uri.parse(subtitleUrl.trim())

            val subtitleConfiguration =
                MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage("fa")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()

            builder.setSubtitleConfigurations(
                listOf(subtitleConfiguration)
            )
        }

        return builder.build()
    }
}
