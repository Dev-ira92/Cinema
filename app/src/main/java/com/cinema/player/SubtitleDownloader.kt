package com.cinema.player

import android.content.Context
import java.io.File

object SubtitleDownloader {

    suspend fun download(
        context: Context,
        subtitleUrl: String
    ): File {
        return com.cinema.data.SubtitleDownloader.download(
            context = context,
            subtitleUrl = subtitleUrl
        )
    }
}
