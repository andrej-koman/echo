package dev.andrej.echo.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.andrej.echo.data.db.EchoDatabase
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** [color] indexes EchoColors.palette; [icon] is a FolderIcon name. */
data class Folder(val id: String, val name: String, val color: Int, val icon: String)

class FolderRepository(
    private val database: EchoDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val queries = database.folderQueries

    val folders: Flow<List<Folder>> = queries.selectAll { id, name, color, icon, _ ->
        Folder(id, name, color.toInt(), icon)
    }.asFlow().mapToList(ioDispatcher)

    suspend fun create(name: String, color: Int, icon: String): String = withContext(ioDispatcher) {
        val id = UUID.randomUUID().toString()
        queries.insert(dev.andrej.echo.data.db.Folder(id, name.trim(), color.toLong(), icon, System.currentTimeMillis()))
        id
    }

    suspend fun update(folder: Folder) = withContext(ioDispatcher) {
        queries.update(folder.name.trim(), folder.color.toLong(), folder.icon, folder.id)
        Unit
    }

    /** Its notes stay, out of any folder. */
    suspend fun delete(id: String) = withContext(ioDispatcher) {
        database.transaction {
            database.transcriptQueries.unfile(id)
            queries.delete(id)
        }
    }
}
