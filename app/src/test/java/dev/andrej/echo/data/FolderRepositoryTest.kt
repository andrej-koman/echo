package dev.andrej.echo.data

import dev.andrej.echo.ui.folders.foldersState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderRepositoryTest {

    @Test
    fun `notes move between folders and fall out when their folder is deleted`() = runTest {
        val database = testDatabase()
        val folders = FolderRepository(database)
        val transcripts = SqlTranscriptRepository(database)
        val work = folders.create(" Work ", color = 2, icon = "Work")
        transcripts.save(id = "a", text = "a", language = "en", durationMs = 0)
        transcripts.save(id = "b", text = "b", language = "en", durationMs = 0)

        transcripts.moveToFolder("a", work)
        assertEquals(listOf(Folder(work, "Work", 2, "Work")), folders.folders.first())
        val state = foldersState(folders.folders.first(), transcripts.transcripts.first())
        assertEquals(1, state.folders.single().noteCount)
        assertEquals(1, state.unfiled)

        folders.delete(work)
        assertEquals(emptyList<Folder>(), folders.folders.first())
        assertEquals(listOf(null, null), transcripts.transcripts.first().map { it.folderId })
    }
}
