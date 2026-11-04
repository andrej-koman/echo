package dev.andrej.echo.ui.profile

import dev.andrej.echo.ai.AiCardState
import dev.andrej.echo.data.FakeSettingsStore
import dev.andrej.echo.data.SqlDerivedRepository
import dev.andrej.echo.data.SqlTranscriptRepository
import dev.andrej.echo.data.StorageCategory
import dev.andrej.echo.data.StorageUsage
import dev.andrej.echo.data.testDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        settings: FakeSettingsStore = FakeSettingsStore(),
        storageUsage: () -> StorageUsage = { StorageUsage(emptyList()) },
    ) = SettingsViewModel(
        settings = settings,
        whisperLanguages = { listOf("en", "sl") },
        transcripts = SqlTranscriptRepository(testDatabase(), dispatcher),
        derived = SqlDerivedRepository(testDatabase(), dispatcher),
        aiCardState = flowOf(AiCardState.Checking),
        litertModelBytes = 977_000_000L,
        onDownloadModel = {},
        onCancelDownload = {},
        onDeleteModel = {},
        storageUsage = storageUsage,
        computeDispatcher = dispatcher,
    )

    @Test
    fun `languages come from Whisper, after auto-detect`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val codes = viewModel.state.value.languages.map { it.code }
        assertEquals("", codes.first())
        assertEquals(setOf("en", "sl"), codes.drop(1).toSet())
    }

    @Test
    fun `chosen language is persisted and reflected in state`() = runTest {
        val settings = FakeSettingsStore()
        val viewModel = viewModel(settings)
        advanceUntilIdle()

        viewModel.setLanguage("sl")

        assertEquals("sl", settings.transcriptionLanguage)
        assertEquals("sl", viewModel.state.value.languageCode)
    }

    @Test
    fun `choosing auto-detect clears the setting`() = runTest {
        val settings = FakeSettingsStore().apply { transcriptionLanguage = "sl" }
        val viewModel = viewModel(settings)
        advanceUntilIdle()

        viewModel.setLanguage("")

        assertNull(settings.transcriptionLanguage)
        assertEquals("Auto-detect", viewModel.state.value.languageLabel)
    }

    @Test
    fun `storage refresh replaces the shown category totals`() = runTest {
        var usage = StorageUsage(listOf(StorageCategory("AI models", 10)))
        val viewModel = viewModel(storageUsage = { usage })
        advanceUntilIdle()
        assertEquals(10, viewModel.state.value.storageBytes)

        usage = StorageUsage(listOf(StorageCategory("Recordings", 7)))
        viewModel.refreshStorage()
        advanceUntilIdle()

        assertEquals(7, viewModel.state.value.storageBytes)
        assertEquals("Recordings", viewModel.state.value.storage.categories.single().label)
    }
}
