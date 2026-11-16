
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
            "آدرس ویدئو خالی است"
        }

        val builder = MediaItem.Builder()
            .setUri(Uri.parse(videoUrl))

        if (!subtitleUrl.isNullOrBlank()) {

            val subtitleConfiguration =
                MediaItem.SubtitleConfiguration.Builder(
                    Uri.parse(subtitleUrl)
                )
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
