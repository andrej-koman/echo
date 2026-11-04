package dev.andrej.echo.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageUsageTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `category totals account for each local data file`() {
        val root = temporaryFolder.root
        val files = File(root, "files")
        val databases = File(root, "databases")
        val preferences = File(root, "shared_prefs")
        val cache = File(root, "cache")
        write(File(files, "models/speech.bin"), 10)
        write(File(files, "models/speech.bin.part"), 3)
        write(File(files, "recordings/recording-id/source"), 7)
        write(File(databases, "echo.db"), 5)
        write(File(databases, "echo.db-wal"), 2)
        write(File(databases, "work/worker.db"), 6)
        write(File(preferences, "settings.xml"), 1)
        write(File(cache, "export/note.txt"), 4)

        val usage = scanStorage(files, databases, preferences, cache)

        assertEquals(38, usage.totalBytes)
        assertEquals(usage.totalBytes, usage.categories.sumOf { it.bytes })
        assertEquals(13, usage.categories.first { it.label == "AI models" }.bytes)
        assertEquals(7, usage.categories.first { it.label == "Recordings" }.bytes)
        assertEquals(7, usage.categories.first { it.label == "Notes and tasks" }.bytes)
        assertEquals(4, usage.categories.first { it.label == "Cache" }.bytes)
        assertEquals(6, usage.categories.first { it.label == "Other app data" }.bytes)
    }

    private fun write(file: File, size: Int) {
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(size))
    }
}
