package dev.andrej.echo.data

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupException(message: String) : IOException(message)

/**
 * One zip holding the SQLite file (with its WAL, if any) and `recordings/`. A restore is only
 * staged here; [applyStaged] swaps it in on the next start, before the database is opened, because
 * the live connection can't be replaced underneath the repositories.
 */
class Backup(
    filesDir: File,
    private val databaseFile: File,
    private val schemaVersion: Long,
    private val schemaVersionOf: (File) -> Long,
) {
    private val recordings = File(filesDir, RECORDINGS)
    private val staging = File(filesDir, "restore")
    private val snapshot = File(filesDir, "backup-snapshot")

    /** [copyDatabase] must copy [databaseFile] and its `-wal` into the given folder while no write can commit. */
    fun write(out: OutputStream, copyDatabase: (File) -> Unit) {
        snapshot.deleteRecursively()
        snapshot.mkdirs()
        try {
            copyDatabase(snapshot)
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write("format=$FORMAT\n".toByteArray())
                zip.closeEntry()
                listOf(DATABASE, DATABASE + WAL).map { File(snapshot, it) }.filter { it.isFile }.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                recordings.walkTopDown().filter { it.isFile }.forEach { file ->
                    zip.putNextEntry(ZipEntry("$RECORDINGS/" + file.relativeTo(recordings).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } finally {
            snapshot.deleteRecursively()
        }
    }

    fun stage(input: InputStream) {
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            var echoBackup = false
            val root = staging.canonicalPath + File.separator
            ZipInputStream(input.buffered()).use { zip ->
                generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
                    val name = entry.name
                    if (name == MANIFEST) {
                        echoBackup = zip.readBytes().decodeToString().lines().contains("format=$FORMAT")
                        return@forEach
                    }
                    if (name != DATABASE && name != DATABASE + WAL && !name.startsWith("$RECORDINGS/")) return@forEach
                    val target = File(staging, name)
                    if (!target.canonicalPath.startsWith(root)) throw BackupException("This backup file is damaged.")
                    target.parentFile!!.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
            val database = File(staging, DATABASE)
            if (!echoBackup || !database.isFile) throw BackupException("This file isn't an Echo backup.")
            if (schemaVersionOf(database) > schemaVersion) {
                throw BackupException("This backup is from a newer version of Echo. Update Echo, then try again.")
            }
            // Opening the staged copy to read its version leaves journal files that belong to no backup.
            File(staging, "$DATABASE-shm").delete()
            File(staging, "$DATABASE-journal").delete()
            File(staging, READY).createNewFile()
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw e
        }
    }

    /** Safe to rerun after a crash part-way: each piece moves only while its staged copy exists. */
    fun applyStaged(): Boolean {
        if (!File(staging, READY).isFile) {
            staging.deleteRecursively()
            return false
        }
        val stagedDatabase = File(staging, DATABASE)
        if (stagedDatabase.isFile) {
            listOf("", WAL, "-shm", "-journal").forEach { File(databaseFile.path + it).delete() }
            databaseFile.parentFile!!.mkdirs()
            move(stagedDatabase, databaseFile)
        }
        File(staging, DATABASE + WAL).takeIf { it.isFile }?.let { move(it, File(databaseFile.path + WAL)) }
        val stagedRecordings = File(staging, RECORDINGS)
        if (stagedRecordings.isDirectory) {
            recordings.deleteRecursively()
            move(stagedRecordings, recordings)
        }
        staging.deleteRecursively()
        return true
    }

    private fun move(from: File, to: File) {
        if (from.renameTo(to)) return
        from.copyRecursively(to, overwrite = true)
        from.deleteRecursively()
    }

    private companion object {
        const val FORMAT = 1
        const val MANIFEST = "echo-backup.txt"
        const val DATABASE = "echo.db"
        const val WAL = "-wal"
        const val RECORDINGS = "recordings"
        const val READY = "ready"
    }
}
