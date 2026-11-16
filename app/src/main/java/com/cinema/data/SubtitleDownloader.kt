
package com.cinema.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object SubtitleDownloader {

    private const val MAX_DOWNLOAD_BYTES = 50L * 1024 * 1024
    private const val MAX_EXTRACTED_BYTES = 100L * 1024 * 1024
    private const val MAX_SUBTITLE_BYTES = 20L * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 200

    private val timestampRegex = Regex(
        """\d{2}:\d{2}:\d{2}[,.]\d{3}\s*-->"""
    )

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun download(
        context: Context,
        subtitleUrl: String
    ): File = withContext(Dispatchers.IO) {

        require(subtitleUrl.isNotBlank()) {
            "Subtitle URL is empty."
        }

        val url = normalizeUrl(subtitleUrl)

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "CinemaApp/1.0 (Android)")
            .header("Accept", "*/*")
            .build()

        client.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Subtitle download failed: HTTP ${response.code}"
                )
            }

            val body = response.body
                ?: throw IllegalStateException(
                    "The server returned an empty file."
                )

            if (body.contentLength() > MAX_DOWNLOAD_BYTES) {
                throw IllegalStateException(
                    "Subtitle file exceeds the size limit."
                )
            }

            val tempFile = File(
                context.cacheDir,
                "subtitle_download_${System.nanoTime()}.tmp"
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
                                    "Subtitle download exceeds the size limit."
                                )
                            }

                            output.write(buffer, 0, count)
                        }

                        output.flush()
                    }
                }

                if (!tempFile.isFile || tempFile.length() == 0L) {
                    throw IllegalStateException(
                        "The downloaded file is empty."
                    )
                }

                if (isZipFile(tempFile)) {
                    extractSrt(tempFile, context.cacheDir)
                } else {
                    normalizeSrtFile(
                        source = tempFile,
                        destination = newSubtitleFile(context.cacheDir)
                    )
                }

            } finally {
                tempFile.delete()
            }
        }
    }

    private fun normalizeUrl(value: String): String {

        val clean = value.trim()

        val normalized = when {
            clean.startsWith("https://", true) -> clean
            clean.startsWith("http://", true) -> clean
            clean.startsWith("//") -> "https:$clean"
            else -> "https://dl.subdl.com/${clean.removePrefix("/")}"
        }

        val parsed = normalized.toHttpUrlOrNull()
            ?: throw IllegalArgumentException(
                "Invalid subtitle URL."
            )

        if (
            parsed.scheme != "https" &&
            parsed.scheme != "http"
        ) {
            throw IllegalArgumentException(
                "Unsupported subtitle URL scheme."
            )
        }

        if (
            parsed.username.isNotEmpty() ||
            parsed.password.isNotEmpty()
        ) {
            throw IllegalArgumentException(
                "Invalid subtitle URL."
            )
        }

        return parsed.toString()
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

    private fun normalizeSrtFile(
        source: File,
        destination: File
    ): File {

        if (source.length() > MAX_SUBTITLE_BYTES) {
            throw IllegalStateException(
                "Subtitle file exceeds the allowed size."
            )
        }

        val bytes = source.readBytes()
        val text = decodeSubtitle(bytes)

        validateSrt(text)

        destination.writeText(
            text,
            Charsets.UTF_8
        )

        return destination
    }

    private fun decodeSubtitle(bytes: ByteArray): String {

        if (bytes.isEmpty()) {
            throw IllegalStateException(
                "Subtitle file is empty."
            )
        }

        // UTF-8 BOM
        if (
            bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(
                bytes,
                3,
                bytes.size - 3,
                StandardCharsets.UTF_8
            )
        }

        // UTF-16 Little Endian BOM
        if (
            bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte()
        ) {
            return String(
                bytes,
                2,
                bytes.size - 2,
                StandardCharsets.UTF_16LE
            )
        }

        // UTF-16 Big Endian BOM
        if (
            bytes.size >= 2 &&
            bytes[0] == 0xFE.toByte() &&
            bytes[1] == 0xFF.toByte()
        ) {
            return String(
                bytes,
                2,
                bytes.size - 2,
                StandardCharsets.UTF_16BE
            )
        }

        // Try strict UTF-8 first.
        try {
            val decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)

            return decoder.decode(
                ByteBuffer.wrap(bytes)
            ).toString()

        } catch (_: Exception) {
            // Continue with legacy Persian encoding.
        }

        // Common legacy encoding for Persian subtitles.
        return try {
            String(
                bytes,
                Charset.forName("windows-1256")
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "Unsupported subtitle text encoding.",
                e
            )
        }
    }

    private fun validateSrt(text: String) {

        val clean = text
            .removePrefix("\uFEFF")
            .trimStart()

        if (clean.isBlank()) {
            throw IllegalStateException(
                "Subtitle file contains no text."
            )
        }

        val lower = clean
            .take(1000)
            .lowercase()

        if (
            lower.startsWith("<!doctype html") ||
            lower.startsWith("<html") ||
            lower.contains("<head") ||
            lower.contains("<body")
        ) {
            throw IllegalStateException(
                "The server returned a web page instead of a subtitle."
            )
        }

        if (!timestampRegex.containsMatchIn(clean)) {
            throw IllegalStateException(
                "The downloaded file is not a valid SRT subtitle."
            )
        }
    }

    private fun extractSrt(
        zipFile: File,
        cacheDir: File
    ): File {

        val candidates = mutableListOf<File>()

        var extractedTotal = 0L
        var entryCount = 0

        try {

            ZipInputStream(zipFile.inputStream()).use { zip ->

                while (true) {

                    val entry = zip.nextEntry ?: break
                    entryCount++

                    if (entryCount > MAX_ZIP_ENTRIES) {
                        throw IllegalStateException(
                            "ZIP archive contains too many files."
                        )
                    }

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

                    val outputBuffer = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var entryBytes = 0L

                    while (true) {

                        val count = zip.read(buffer)

                        if (count == -1) break

                        entryBytes += count
                        extractedTotal += count

                        if (entryBytes > MAX_SUBTITLE_BYTES) {
                            throw IllegalStateException(
                                "A subtitle file in the ZIP is too large."
                            )
                        }

                        if (extractedTotal > MAX_EXTRACTED_BYTES) {
                            throw IllegalStateException(
                                "Extracted files exceed the size limit."
                            )
                        }

                        outputBuffer.write(
                            buffer,
                            0,
                            count
                        )
                    }

                    zip.closeEntry()

                    val content = outputBuffer.toByteArray()

                    try {

                        val decoded = decodeSubtitle(content)

                        validateSrt(decoded)

                        val destination = newSubtitleFile(cacheDir)

                        destination.writeText(
                            decoded,
                            Charsets.UTF_8
                        )

                        candidates.add(destination)

                    } catch (_: Exception) {
                        // Ignore invalid SRT entries and inspect the next one.
                    }
                }
            }

            if (candidates.isEmpty()) {
                throw IllegalStateException(
                    "No valid SRT subtitle was found in the ZIP archive."
                )
            }

            val selected = candidates.maxByOrNull {
                it.length()
            } ?: throw IllegalStateException(
                "Could not select a subtitle file."
            )

            candidates
                .filter { it != selected }
                .forEach { it.delete() }

            return selected

        } catch (e: Exception) {

            candidates.forEach {
                it.delete()
            }

            throw IllegalStateException(
                "Could not extract subtitle archive: ${e.message}",
                e
            )
        }
    }

    private fun newSubtitleFile(cacheDir: File): File {
        return File(
            cacheDir,
            "cinema_subtitle_${System.nanoTime()}.srt"
        )
    }
}
