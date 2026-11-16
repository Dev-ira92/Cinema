
package com.cinema.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object SubtitleDownloader {

    private const val MAX_DOWNLOAD_BYTES =
        50L * 1024 * 1024

    private const val MAX_EXTRACTED_BYTES =
        100L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun download(
        context: Context,
        subtitleUrl: String
    ): File = withContext(Dispatchers.IO) {

        require(subtitleUrl.isNotBlank()) {
            "آدرس زیرنویس خالی است"
        }

        val url = normalizeUrl(subtitleUrl)

        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Android) CinemaApp/1.0"
            )
            .build()

        client.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "دانلود ناموفق بود: HTTP ${response.code}"
                )
            }

            val body = response.body
                ?: throw IllegalStateException(
                    "سرور فایل خالی ارسال کرد"
                )

            if (
                body.contentLength() > MAX_DOWNLOAD_BYTES
            ) {
                throw IllegalStateException(
                    "حجم فایل بیش از حد مجاز است"
                )
            }

            val tempFile = File(
                context.cacheDir,
                "subtitle_${System.nanoTime()}.tmp"
            )

            try {

                body.byteStream().use { input ->

                    FileOutputStream(tempFile).use { output ->

                        val buffer = ByteArray(8192)
                        var total = 0L

                        while (true) {

                            val count = input.read(buffer)

                            if (count == -1) break

                            total += count

                            if (total > MAX_DOWNLOAD_BYTES) {
                                throw IllegalStateException(
                                    "حجم دانلود بیش از حد مجاز است"
                                )
                            }

                            output.write(buffer, 0, count)
                        }

                        output.flush()
                    }
                }

                if (tempFile.length() == 0L) {
                    throw IllegalStateException(
                        "فایل دانلودشده خالی است"
                    )
                }

                if (isZipFile(tempFile)) {

                    extractSrt(
                        tempFile,
                        context.cacheDir
                    )

                } else {

                    if (!hasSrtTimestamp(tempFile)) {
                        throw IllegalStateException(
                            "فایل دریافت‌شده زیرنویس SRT معتبر نیست"
                        )
                    }

                    val destination = File(
                        context.cacheDir,
                        "subtitle_${System.nanoTime()}.srt"
                    )

                    tempFile.copyTo(
                        destination,
                        overwrite = true
                    )

                    destination
                }

            } finally {

                tempFile.delete()
            }
        }
    }

    private fun normalizeUrl(value: String): String {

        val clean = value.trim()

        if (
            clean.startsWith("https://", true) ||
            clean.startsWith("http://", true)
        ) {
            return clean
        }

        if (clean.startsWith("//")) {
            return "https:$clean"
        }

        return "https://dl.subdl.com/" +
            clean.removePrefix("/")
    }

    private fun isZipFile(file: File): Boolean {

        val signature = ByteArray(4)

        file.inputStream().use {
            if (it.read(signature) < 4) return false
        }

        return signature[0] == 0x50.toByte() &&
            signature[1] == 0x4B.toByte() &&
            signature[2] == 0x03.toByte() &&
            signature[3] == 0x04.toByte()
    }

    private fun hasSrtTimestamp(file: File): Boolean {

        if (!file.isFile || file.length() == 0L) {
            return false
        }

        val sample = ByteArray(
            minOf(file.length(), 8192L).toInt()
        )

        val count = file.inputStream().use {
            it.read(sample)
        }

        if (count <= 0) return false

        val content = String(
            sample,
            0,
            count,
            Charsets.UTF_8
        ).removePrefix("\uFEFF")

        return Regex(
            """\d{2}:\d{2}:\d{2}[,.]\d{3}\s*-->"""
        ).containsMatchIn(content)
    }

    private fun extractSrt(
        zipFile: File,
        cacheDir: File
    ): File {

        val candidates = mutableListOf<File>()
        var extractedTotal = 0L

        try {

            ZipInputStream(zipFile.inputStream()).use { zip ->

                while (true) {

                    val entry = zip.nextEntry ?: break

                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }

                    val safeName = entry.name
                        .replace('\\', '/')
                        .substringAfterLast('/')

                    if (
                        safeName.isBlank() ||
                        !safeName.endsWith(".srt", true)
                    ) {
                        zip.closeEntry()
                        continue
                    }

                    val destination = File(
                        cacheDir,
                        "subtitle_${System.nanoTime()}_${
                            safeName.replace(
                                Regex("[^A-Za-z0-9._-]"),
                                "_"
                            )
                        }"
                    )

                    try {

                        FileOutputStream(destination).use { output ->

                            val buffer = ByteArray(8192)

                            while (true) {

                                val count = zip.read(buffer)

                                if (count == -1) break

                                extractedTotal += count

                                if (
                                    extractedTotal >
                                    MAX_EXTRACTED_BYTES
                                ) {
                                    throw IllegalStateException(
                                        "حجم فایل استخراج‌شده بیش از حد مجاز است"
                                    )
                                }

                                output.write(buffer, 0, count)
                            }

                            output.flush()
                        }

                        if (hasSrtTimestamp(destination)) {
                            candidates.add(destination)
                        } else {
                            destination.delete()
                        }

                    } finally {
                        zip.closeEntry()
                    }
                }
            }

            if (candidates.isEmpty()) {
                throw IllegalStateException(
                    "فایل SRT معتبر در ZIP پیدا نشد"
                )
            }

            val selected = candidates.maxByOrNull {
                it.length()
            } ?: throw IllegalStateException(
                "انتخاب زیرنویس ناموفق بود"
            )

            candidates
                .filter { it != selected }
                .forEach { it.delete() }

            return selected

        } catch (e: Exception) {

            candidates.forEach { it.delete() }

            throw IllegalStateException(
                "استخراج ZIP ناموفق بود: ${e.message}",
                e
            )
        }
    }
}
