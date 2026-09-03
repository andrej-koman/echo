package dev.andrej.echo.ai

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class HttpModelDownloader(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelDownloader {

    override suspend fun download(
        url: String,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ) = withContext(ioDispatcher) {
        val alreadyHave = if (target.isFile) target.length() else 0

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            if (alreadyHave > 0) setRequestProperty("Range", "bytes=$alreadyHave-")
        }

        try {
            val resumed = connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (connection.responseCode !in 200..299) {
                throw IOException("download failed with HTTP ${connection.responseCode}")
            }

            // A server that ignored the Range header sends the whole file from byte zero, so the
            // part file has to go rather than end up with the first bytes written twice.
            val startAt = if (resumed) alreadyHave else 0
            val total = startAt + connection.contentLengthLong.coerceAtLeast(0)

            var written = startAt
            onProgress(written, total)

            connection.inputStream.use { input ->
                java.io.FileOutputStream(target, resumed).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var lastReported = written

                    while (true) {
                        currentCoroutineContext().ensureActive()

                        val read = input.read(buffer)
                        if (read == -1) break

                        output.write(buffer, 0, read)
                        written += read

                        if (written - lastReported >= PROGRESS_STEP_BYTES) {
                            lastReported = written
                            onProgress(written, total)
                        }
                    }
                }
            }

            onProgress(written, total)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val BUFFER_BYTES = 64 * 1024
        const val PROGRESS_STEP_BYTES = 1024 * 1024L
    }
}
