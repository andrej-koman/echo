package dev.andrej.echo.ai

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val SPEC = ModelSpec(url = "https://example.test/model", fileName = "model.litertlm", bytes = 1_000)

/** Writes [payload] to the target, resuming from whatever is already there. */
private class FakeDownloader(
    private val payload: ByteArray = ByteArray(1_000) { 1 },
    private val failWith: Exception? = null,
    private val pauseAt: CompletableDeferred<Unit>? = null,
) : ModelDownloader {

    val requestedResumeOffsets = mutableListOf<Long>()

    override suspend fun download(url: String, target: File, onProgress: (Long, Long) -> Unit) {
        val alreadyHave = if (target.isFile) target.length() else 0
        requestedResumeOffsets += alreadyHave

        failWith?.let { throw it }

        onProgress(alreadyHave, payload.size.toLong())
        pauseAt?.await()

        target.appendBytes(payload.copyOfRange(alreadyHave.toInt(), payload.size))
        onProgress(payload.size.toLong(), payload.size.toLong())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ModelStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun store(downloader: ModelDownloader, directory: File = tempFolder.newFolder()) =
        ModelStore(directory, SPEC, downloader)

    @Test
    fun `a fresh store has no model`() = runTest {
        val store = store(FakeDownloader())

        assertEquals(ModelState.Absent, store.state.value)
        assertNull(store.fileOrNull())
    }

    @Test
    fun `a completed download becomes ready`() = runTest {
        val store = store(FakeDownloader())

        store.download()

        val state = store.state.value as ModelState.Ready
        assertEquals(1_000, state.file.length())
        assertEquals(state.file, store.fileOrNull())
    }

    @Test
    fun `the part file is renamed, not left behind`() = runTest {
        val directory = tempFolder.newFolder()

        store(FakeDownloader(), directory).download()

        assertTrue(directory.resolve("model.litertlm").isFile)
        assertFalse(directory.resolve("model.litertlm.part").exists())
    }

    @Test
    fun `progress is reported against the published size`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = store(FakeDownloader(pauseAt = gate))

        val job = launch { store.download() }
        runCurrent()

        val state = store.state.value as ModelState.Downloading
        assertEquals(1_000, state.totalBytes)
        assertEquals(0f, state.fraction, 0.001f)

        gate.complete(Unit)
        job.join()
        assertTrue(store.state.value is ModelState.Ready)
    }

    @Test
    fun `a failed download reports why and leaves no model`() = runTest {
        val store = store(FakeDownloader(failWith = IOException("no network")))

        store.download()

        assertEquals("no network", (store.state.value as ModelState.Failed).message)
        assertNull(store.fileOrNull())
    }

    @Test
    fun `an interrupted download resumes from what it already has`() = runTest {
        val directory = tempFolder.newFolder()
        directory.mkdirs()
        directory.resolve("model.litertlm.part").writeBytes(ByteArray(400) { 1 })
        val downloader = FakeDownloader()

        store(downloader, directory).download()

        assertEquals(listOf(400L), downloader.requestedResumeOffsets)
        assertEquals(1_000, directory.resolve("model.litertlm").length())
    }

    @Test
    fun `a leftover part file does not read as an active download`() = runTest {
        val directory = tempFolder.newFolder()
        directory.resolve("model.litertlm.part").writeBytes(ByteArray(400))

        assertEquals(ModelState.Absent, store(FakeDownloader(), directory).state.value)
    }

    @Test
    fun `a model on disk is found at construction`() = runTest {
        val directory = tempFolder.newFolder()
        directory.resolve("model.litertlm").writeBytes(ByteArray(1_000))

        assertTrue(store(FakeDownloader(), directory).state.value is ModelState.Ready)
    }

    @Test
    fun `an empty file on disk does not count as a model`() = runTest {
        val directory = tempFolder.newFolder()
        directory.resolve("model.litertlm").createNewFile()

        assertEquals(ModelState.Absent, store(FakeDownloader(), directory).state.value)
    }

    @Test
    fun `downloading again once ready does not refetch`() = runTest {
        val downloader = FakeDownloader()
        val store = store(downloader)
        store.download()

        store.download()

        assertEquals(1, downloader.requestedResumeOffsets.size)
    }

    @Test
    fun `delete removes the model, the part file and the runtime's weight cache`() = runTest {
        val directory = tempFolder.newFolder()
        val store = store(FakeDownloader(), directory)
        store.download()
        directory.resolve("model.litertlm.part").writeBytes(ByteArray(10))
        // LiteRT-LM leaves one of these beside the model; observed on device.
        directory.resolve("model.litertlm_1788421682_977184032_mldrift_weight_cache.bin")
            .writeBytes(ByteArray(208))

        store.delete()

        assertEquals(ModelState.Absent, store.state.value)
        assertNull(store.fileOrNull())
        assertEquals(emptyList<File>(), directory.listFiles()!!.toList())
    }

    @Test
    fun `delete leaves other files in the directory alone`() = runTest {
        val directory = tempFolder.newFolder()
        val store = store(FakeDownloader(), directory)
        store.download()
        directory.resolve("unrelated.txt").writeText("keep me")

        store.delete()

        assertEquals(listOf("unrelated.txt"), directory.listFiles()!!.map { it.name })
    }
}
