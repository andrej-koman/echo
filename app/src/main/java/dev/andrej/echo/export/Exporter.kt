package dev.andrej.echo.export

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ExportFormat { Text, Markdown, Subtitles, Audio }

/** Writes an export to the cache and hands it to the system share sheet. */
class Exporter(private val context: Context) {

    private val dir get() = File(context.cacheDir, "export")

    suspend fun share(format: ExportFormat, note: ExportNote, options: ExportOptions, audio: File?) {
        val (file, mime) = withContext(Dispatchers.IO) {
            dir.deleteRecursively()
            dir.mkdirs()
            val name = fileName(note.title)
            when (format) {
                ExportFormat.Text -> write("$name.txt", exportText(note, options)) to "text/plain"
                ExportFormat.Markdown -> write("$name.md", exportMarkdown(note, options)) to "text/markdown"
                ExportFormat.Subtitles -> write("$name.srt", exportSrt(note, options)) to "application/x-subrip"
                ExportFormat.Audio -> {
                    val source = audio ?: return@withContext null
                    val mime = mimeOf(source)
                    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "m4a"
                    source.copyTo(File(dir, "$name.$extension"), overwrite = true) to mime
                }
            }
        } ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, note.title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, note.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun write(name: String, content: String) = File(dir, name).apply { writeText(content) }

    /** `source` has no extension: a recording is AAC in MP4, an import is whatever was picked. */
    private fun mimeOf(file: File): String = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        }
    }.getOrNull() ?: "audio/mp4"
}
