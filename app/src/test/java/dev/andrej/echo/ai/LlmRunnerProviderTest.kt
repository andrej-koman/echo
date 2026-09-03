package dev.andrej.echo.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private class CountingRunner(
    private val result: LlmAvailability,
) : LlmRunner {
    var probes = 0

    override suspend fun availability(): LlmAvailability {
        probes++
        return result
    }

    override suspend fun warmup() = Unit

    override suspend fun generate(prompt: String, temperature: Float) = "unused"
}

class LlmRunnerProviderTest {

    @Test
    fun `the first usable runner wins`() = runTest {
        val nano = CountingRunner(LlmAvailability.Ready)
        val fallback = CountingRunner(LlmAvailability.Ready)

        assertSame(nano, LlmRunnerProvider(listOf(nano, fallback), log = {}).runner())
        assertEquals(0, fallback.probes)
    }

    @Test
    fun `an unsupported runner is skipped`() = runTest {
        val nano = CountingRunner(LlmAvailability.Unsupported("no AICore"))
        val fallback = CountingRunner(LlmAvailability.Ready)

        assertSame(fallback, LlmRunnerProvider(listOf(nano, fallback), log = {}).runner())
    }

    @Test
    fun `a runner awaiting a download still wins over the next one`() = runTest {
        val nano = CountingRunner(LlmAvailability.NeedsDownload(bytes = 550_000_000))
        val fallback = CountingRunner(LlmAvailability.Ready)

        assertSame(nano, LlmRunnerProvider(listOf(nano, fallback), log = {}).runner())
    }

    @Test
    fun `all unsupported yields a runner that reports why`() = runTest {
        val provider = LlmRunnerProvider(
            candidates = listOf(
                CountingRunner(LlmAvailability.Unsupported("no AICore")),
                CountingRunner(LlmAvailability.Unsupported("not enough memory")),
            ),
            log = {},
        )

        val runner = provider.runner()
        val availability = runner.availability() as LlmAvailability.Unsupported

        assertTrue(availability.reason.contains("no AICore"))
        assertTrue(availability.reason.contains("not enough memory"))
    }

    @Test
    fun `an empty candidate list yields a runner, not a crash`() = runTest {
        val runner = LlmRunnerProvider(emptyList(), log = {}).runner()

        assertTrue(runner.availability() is LlmAvailability.Unsupported)
    }

    @Test
    fun `the choice is probed once and remembered`() = runTest {
        val nano = CountingRunner(LlmAvailability.Ready)
        val provider = LlmRunnerProvider(listOf(nano), log = {})

        repeat(5) { provider.runner() }

        assertEquals(1, nano.probes)
    }
}
