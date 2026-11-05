package dev.andrej.echo.ai

import dev.andrej.echo.data.FakeSettingsStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotesAiSelectionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun store(model: NotesAiModel, downloader: ModelDownloader) =
        ModelStore(tempFolder.newFolder(), model.spec, downloader)

    private class SuccessfulDownloader : ModelDownloader {
        override suspend fun download(url: String, target: File, onProgress: (Long, Long) -> Unit) {
            target.writeBytes(byteArrayOf(1))
            onProgress(1, 1)
        }
    }

    @Test
    fun `none survives restart without falling back to a downloaded model`() = runBlocking {
        val default = store(NotesAiModel.Default, SuccessfulDownloader()).also { it.download() }
        val fast = store(NotesAiModel.Fast, SuccessfulDownloader()).also { it.download() }
        val stores = mapOf(NotesAiModel.Default to default, NotesAiModel.Fast to fast)
        val settings = FakeSettingsStore()
        val selection = NotesAiSelection(settings, stores)
        selection.choose(NotesAiModel.Fast)
        fast.delete()
        selection.disable()

        val restarted = NotesAiSelection(settings, stores)
        assertEquals(NotesAiModel.Fast, restarted.active.value)
        assertEquals(false, restarted.statuses(NotesAiModel.entries.map {
            stores[it]?.state?.value ?: ModelState.Absent
        }).any { it.active })
        assertEquals(ModelChoice.Activated, restarted.choose(NotesAiModel.Default))
        assertEquals(NotesAiModel.Default.id, settings.notesAiModel)
    }

    @Test
    fun `undownloaded choice remains pending until its model is ready`() = runBlocking {
        val settings = FakeSettingsStore()
        val fast = store(NotesAiModel.Fast, SuccessfulDownloader())
        val selection = NotesAiSelection(settings, mapOf(NotesAiModel.Fast to fast))

        assertEquals(ModelChoice.NeedsDownload, selection.choose(NotesAiModel.Fast))
        assertEquals(NotesAiModel.Default.id, settings.notesAiModel)
        assertEquals(null, settings.pendingNotesAiModel)

        selection.startDownload(NotesAiModel.Fast)
        assertEquals(NotesAiModel.Fast.id, settings.pendingNotesAiModel)

        fast.download()
        selection.modelStateChanged(NotesAiModel.Fast)

        assertEquals(NotesAiModel.Fast.id, settings.notesAiModel)
        assertEquals(null, settings.pendingNotesAiModel)
    }

    @Test
    fun `a cancelled download keeps the current model active`() {
        val settings = FakeSettingsStore().apply { notesAiModel = NotesAiModel.Default.id }
        val selection = NotesAiSelection(settings, emptyMap())
        selection.choose(NotesAiModel.Fast)
        selection.startDownload(NotesAiModel.Fast)

        selection.downloadEnded(NotesAiModel.Fast)

        assertEquals(NotesAiModel.Default.id, settings.notesAiModel)
        assertEquals(null, settings.pendingNotesAiModel)
    }

    @Test
    fun `a failed download keeps the current model active`() = runBlocking {
        val settings = FakeSettingsStore()
        val fast = store(NotesAiModel.Fast, object : ModelDownloader {
            override suspend fun download(url: String, target: File, onProgress: (Long, Long) -> Unit) {
                throw IOException("offline")
            }
        })
        val selection = NotesAiSelection(settings, mapOf(NotesAiModel.Fast to fast))
        selection.startDownload(NotesAiModel.Fast)

        fast.download()
        selection.modelStateChanged(NotesAiModel.Fast)

        assertEquals(NotesAiModel.Default.id, settings.notesAiModel)
        assertEquals(null, settings.pendingNotesAiModel)
    }

    @Test
    fun `a ready choice activates immediately`() = runBlocking {
        val fast = store(NotesAiModel.Fast, SuccessfulDownloader()).also { it.download() }
        val settings = FakeSettingsStore()
        val selection = NotesAiSelection(settings, mapOf(NotesAiModel.Fast to fast))

        assertEquals(ModelChoice.Activated, selection.choose(NotesAiModel.Fast))
        assertEquals(NotesAiModel.Fast.id, settings.notesAiModel)
    }

    @Test
    fun `a completed pending download activates after app restart`() = runBlocking {
        val fast = store(NotesAiModel.Fast, SuccessfulDownloader()).also { it.download() }
        val settings = FakeSettingsStore().apply { pendingNotesAiModel = NotesAiModel.Fast.id }

        NotesAiSelection(settings, mapOf(NotesAiModel.Fast to fast))

        assertEquals(NotesAiModel.Fast.id, settings.notesAiModel)
        assertEquals(null, settings.pendingNotesAiModel)
    }

    @Test
    fun `an interrupted download remains pending after app restart`() {
        val settings = FakeSettingsStore().apply { pendingNotesAiModel = NotesAiModel.Fast.id }
        val selection = NotesAiSelection(settings, emptyMap())

        assertEquals(NotesAiModel.Default, selection.active.value)
        assertEquals(NotesAiModel.Fast.id, settings.pendingNotesAiModel)
    }

    @Test
    fun `removing the active model switches to another downloaded model`() = runBlocking {
        val default = store(NotesAiModel.Default, SuccessfulDownloader()).also { it.download() }
        val fast = store(NotesAiModel.Fast, SuccessfulDownloader()).also { it.download() }
        val settings = FakeSettingsStore()
        val selection = NotesAiSelection(settings, mapOf(NotesAiModel.Default to default, NotesAiModel.Fast to fast))
        selection.choose(NotesAiModel.Fast)

        fast.delete()
        selection.modelRemoved(NotesAiModel.Fast)

        assertEquals(NotesAiModel.Default, selection.active.value)
        assertEquals(NotesAiModel.Default.id, settings.notesAiModel)
    }
}
