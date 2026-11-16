
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

    private const val MAX_DOWNLOAD_BYTES = 50L * 1024 * 1024
    private const val MAX_EXTRACTED_BYTES = 100L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Downloads an SRT subtitle.
     *
     * Supports:
     * - Direct .srt links
     * - ZIP archives containing an .srt file
     *
     * Returns the extracted/downloaded SRT file.
     */
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
                    "دانلود زیرنویس ناموفق بود: HTTP ${response.code}"
                )
            }

            val body = response.body
                ?: throw IllegalStateException(
                    "سرور فایل خالی ارسال کرد"
                )

            val contentLength = body.contentLength()

            if (
                contentLength > MAX_DOWNLOAD_BYTES
            ) {
                throw IllegalStateException(
                    "حجم فایل زیرنویس بیش از حد مجاز است"
                )
            }

            val tempFile = File(
                context.cacheDir,
                "subtitle_download_${System.currentTimeMillis()}.tmp"
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
                                    "حجم فایل از حد مجاز بیشتر شد"
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

                val outputFile = if (
                    isZipFile(tempFile)
                ) {
                    extractSrt(
                        tempFile,
                        context.cacheDir
                    )
                } else {

                    if (!isSrtFile(tempFile)) {
                        throw IllegalStateException(
                            "فایل دریافت‌شده زیرنویس SRT معتبر نیست"
                        )
                    }

                    File(
                        context.cacheDir,
                        "subtitle_${System.currentTimeMillis()}.srt"
                    ).also { destination ->
                        if (!tempFile.renameTo(destination)) {
                            tempFile.copyTo(
                                destination,
                                overwrite = true
                            )
                        }
                    }
                }

                outputFile

            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    /**
     * Converts relative SubDL download paths into absolute URLs.
     */
    private fun normalizeUrl(value: String): String {

        val clean = value.trim()

        if (
            clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("http://", ignoreCase = true)
        ) {
            return clean
        }

        if (clean.startsWith("//")) {
            return "https:$clean"
        }

        return "https://dl.subdl.com/" +
            clean.removePrefix("/")
    }

    /**
     * Detect ZIP by its file signature, not by its extension.
     */
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

    /**
     * Basic SRT validation.
     */
    private fun isSrtFile(file: File): Boolean {

        if (!file.isFile || file.length() == 0L) {
            return false
        }

        val extensionLooksCorrect =
            file.name.endsWith(".srt", true)

        val beginning = ByteArray(512)

        val count = file.inputStream().use {
            it.read(beginning)
        }

        if (count <= 0) return false

        val sample = String(
            beginning,
            0,
            count,
            Charsets.UTF_8
        ).removePrefix("\uFEFF")

        val hasTimestamp =
            Regex(
                """\d{2}:\d{2}:\d{2}[,.]\d{3}\s*-->"""
            ).containsMatchIn(sample)

        return extensionLooksCorrect && hasTimestamp
    }

    /**
     * Extracts the first valid SRT file from a ZIP archive.
     * Path traversal entries are ignored.
     */
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

                    val entryName = entry.name
                        .replace('\\', '/')

                    val safeName = entryName.substringAfterLast('/')

                    if (
                        safeName.isBlank() ||
                        safeName.startsWith(".") ||
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

                    var entrySize = 0L

                    try {

                        FileOutputStream(destination).use { output ->

                            val buffer = ByteArray(8192)

                            while (true) {

                                val count = zip.read(buffer)

                                if (count == -1) break

                                entrySize += count
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

                        if (isSrtFile(destination)) {
                            candidates.add(destination)
                        } else {
                            destination.delete()
                        }

                    } catch (e: Exception) {

                        destination.delete()
                        throw e

                    } finally {
                        zip.closeEntry()
                    }
                }
            }

            if (candidates.isEmpty()) {
                throw IllegalStateException(
                    "داخل فایل ZIP زیرنویس SRT معتبر پیدا نشد"
                )
            }

            // Prefer the largest valid subtitle file.
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
                "استخراج زیرنویس از ZIP ناموفق بود: ${e.message}",
                e
            )
        }
    }
}
