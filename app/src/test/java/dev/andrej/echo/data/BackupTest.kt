package dev.andrej.echo.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val root = Files.createTempDirectory("backup").toFile()
    private val files = File(root, "files")
    private val database = File(root, "databases/echo.db")

    private fun backup(version: Long = 8, stagedVersion: Long = 8) = Backup(files, database, version) { stagedVersion }

    private fun saved(): ByteArray {
        database.parentFile!!.mkdirs()
        database.writeText("db")
        File(database.path + "-wal").writeText("wal")
        File(files, "recordings/a/audio.m4a").apply { parentFile!!.mkdirs() }.writeText("audio")
        return ByteArrayOutputStream().also { out ->
            backup().write(out) { dir ->
                database.copyTo(File(dir, "echo.db"))
                File(database.path + "-wal").copyTo(File(dir, "echo.db-wal"))
            }
        }.toByteArray()
    }

    @Test fun `a staged backup replaces the database and recordings on the next start`() {
        val zip = saved()
        database.writeText("newer db")
        File(database.path + "-shm").writeText("shm")
        File(files, "recordings/a").deleteRecursively()
        File(files, "recordings/b/audio.m4a").apply { parentFile!!.mkdirs() }.writeText("other")

        backup().stage(ByteArrayInputStream(zip))
        assertEquals("newer db", database.readText())
        assertTrue(backup().applyStaged())

        assertEquals("db", database.readText())
        assertEquals("wal", File(database.path + "-wal").readText())
        assertFalse(File(database.path + "-shm").exists())
        assertEquals("audio", File(files, "recordings/a/audio.m4a").readText())
        assertFalse(File(files, "recordings/b").exists())
        assertFalse(File(files, "restore").exists())
        assertFalse(backup().applyStaged())
    }

    @Test fun `newer or foreign files are refused and leave nothing staged`() {
        val zip = saved()
        assertThrows(BackupException::class.java) { backup(stagedVersion = 9).stage(ByteArrayInputStream(zip)) }
        assertFalse(File(files, "restore").exists())

        val foreign = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry("echo.db")); it.write(1) }
        }.toByteArray()
        assertThrows(BackupException::class.java) { backup().stage(ByteArrayInputStream(foreign)) }
        assertFalse(backup().applyStaged())
        assertEquals("db", database.readText())
    }

    @Test fun `entries cannot escape the staging folder`() {
        val evil = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use {
                it.putNextEntry(ZipEntry("echo-backup.txt")); it.write("format=1\n".toByteArray())
                it.putNextEntry(ZipEntry("recordings/../../escaped")); it.write(1)
            }
        }.toByteArray()
        assertThrows(BackupException::class.java) { backup().stage(ByteArrayInputStream(evil)) }
        assertFalse(File(files, "escaped").exists())
    }
}
