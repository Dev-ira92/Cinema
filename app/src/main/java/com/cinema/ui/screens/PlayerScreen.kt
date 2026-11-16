
package com.cinema.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes

object SubtitlePlayer {

    /**
     * Creates a Media3 MediaItem with an external SRT subtitle.
     *
     * videoUrl:
     * The movie or episode video URL.
     *
     * subtitleUrl:
     * The local SRT file URI or a supported subtitle URL.
     */
    fun createMediaItem(
        videoUrl: String,
        subtitleUrl: String
    ): MediaItem {

        require(videoUrl.isNotBlank()) {
            "آدرس ویدئو خالی است"
        }

        require(subtitleUrl.isNotBlank()) {
            "آدرس زیرنویس خالی است"
        }

        val videoUri = Uri.parse(videoUrl)
        val subtitleUri = Uri.parse(subtitleUrl)

        val subtitleConfiguration =
            MediaItem.SubtitleConfiguration.Builder(
                subtitleUri
            )
                .setMimeType(
                    MimeTypes.APPLICATION_SUBRIP
                )
                .setLanguage("fa")
                .setSelectionFlags(
                    C.SELECTION_FLAG_DEFAULT
                )
                .build()

        return MediaItem.Builder()
            .setUri(videoUri)
            .setSubtitleConfigurations(
                listOf(subtitleConfiguration)
            )
            .build()
    }
}
