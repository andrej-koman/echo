package dev.andrej.echo.data

import java.io.File

data class StorageCategory(val label: String, val bytes: Long)

data class StorageUsage(val categories: List<StorageCategory>) {
    val totalBytes: Long get() = categories.sumOf { it.bytes }
}

fun scanStorage(
    filesDir: File,
    databasesDir: File,
    preferencesDir: File,
    cacheDir: File,
    noBackupDir: File? = null,
): StorageUsage {
    val databaseEntries = databasesDir.listFiles().orEmpty()
    val echoDatabaseFiles = databaseEntries.filter { it.isFile && (it.name == "echo.db" || it.name.startsWith("echo.db-")) }
    return StorageUsage(
        listOf(
            StorageCategory("AI models", sizeOf(File(filesDir, "models"))),
            StorageCategory("Recordings", sizeOf(File(filesDir, "recordings"))),
            StorageCategory("Notes and tasks", echoDatabaseFiles.sumOf { it.length() }),
            StorageCategory("Settings", sizeOf(preferencesDir)),
            StorageCategory("Cache", sizeOf(cacheDir)),
            StorageCategory(
                "Other app data",
                filesDir.listFiles().orEmpty()
                    .filter { it.name != "models" && it.name != "recordings" }
                    .sumOf(::sizeOf) +
                    databaseEntries.filterNot { it in echoDatabaseFiles }.sumOf(::sizeOf) +
                    (noBackupDir?.let(::sizeOf) ?: 0L),
            ),
        ).filter { it.bytes > 0 },
    )
}

private fun sizeOf(file: File): Long = when {
    file.isFile -> file.length()
    file.isDirectory -> file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    else -> 0L
}
