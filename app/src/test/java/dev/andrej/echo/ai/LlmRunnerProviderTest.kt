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

        assertSame(
            nano,
            LlmRunnerProvider(mapOf(LlmBackend.NANO to nano, LlmBackend.LITERT to fallback), log = {}).runner(),
        )
        assertEquals(0, fallback.probes)
    }

    @Test
    fun `an unsupported runner is skipped`() = runTest {
        val nano = CountingRunner(LlmAvailability.Unsupported("no AICore"))
        val fallback = CountingRunner(LlmAvailability.Ready)

        assertSame(
            fallback,
            LlmRunnerProvider(mapOf(LlmBackend.NANO to nano, LlmBackend.LITERT to fallback), log = {}).runner(),
        )
    }

    @Test
    fun `a runner awaiting a download still wins over the next one`() = runTest {
        val nano = CountingRunner(LlmAvailability.NeedsDownload(bytes = 550_000_000))
        val fallback = CountingRunner(LlmAvailability.Ready)

        assertSame(
            nano,
            LlmRunnerProvider(mapOf(LlmBackend.NANO to nano, LlmBackend.LITERT to fallback), log = {}).runner(),
        )
    }

    @Test
    fun `all unsupported yields a runner that reports why`() = runTest {
        val provider = LlmRunnerProvider(
            candidates = mapOf(
                LlmBackend.NANO to CountingRunner(LlmAvailability.Unsupported("no AICore")),
                LlmBackend.LITERT to CountingRunner(LlmAvailability.Unsupported("not enough memory")),
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
        val runner = LlmRunnerProvider(emptyMap(), log = {}).runner()

        assertTrue(runner.availability() is LlmAvailability.Unsupported)
    }

    @Test
    fun `the choice is probed once and remembered`() = runTest {
        val nano = CountingRunner(LlmAvailability.Ready)
        val provider = LlmRunnerProvider(mapOf(LlmBackend.NANO to nano), log = {})

        repeat(5) { provider.runner() }

        assertEquals(1, nano.probes)
    }

    @Test
    fun `invalidate forces a re-probe on the next call`() = runTest {
        val nano = CountingRunner(LlmAvailability.Unsupported("no AICore"))
        val fallback = CountingRunner(LlmAvailability.Ready)
        val provider = LlmRunnerProvider(mapOf(LlmBackend.NANO to nano, LlmBackend.LITERT to fallback), log = {})

        assertSame(fallback, provider.runner())
        provider.invalidate()
        assertSame(fallback, provider.runner())

        assertEquals(2, nano.probes)
    }

    @Test
    fun `a changed availability is picked up only after invalidate`() = runTest {
        var available = false
        val flexible = object : LlmRunner {
            override suspend fun availability(): LlmAvailability =
                if (available) LlmAvailability.Ready else LlmAvailability.Unsupported("not yet")

            override suspend fun warmup() = Unit
            override suspend fun generate(prompt: String, temperature: Float) = "unused"
        }
        val provider = LlmRunnerProvider(mapOf(LlmBackend.NANO to flexible), log = {})

        assertTrue(provider.runner().availability() is LlmAvailability.Unsupported)

        available = true
        assertTrue(provider.runner().availability() is LlmAvailability.Unsupported)

        provider.invalidate()
        assertEquals(LlmAvailability.Ready, provider.runner().availability())
    }

    @Test
    fun `a preferred backend is used even if a candidate earlier in the map would win`() = runTest {
        val nano = CountingRunner(LlmAvailability.Ready)
        val litert = CountingRunner(LlmAvailability.Ready)
        val provider = LlmRunnerProvider(
            candidates = mapOf(LlmBackend.NANO to nano, LlmBackend.LITERT to litert),
            preferredBackend = { LlmBackend.LITERT },
            log = {},
        )

        assertSame(litert, provider.runner())
        assertEquals(0, nano.probes)
    }

    @Test
    fun `a preferred backend that is unsupported does not fall back to another candidate`() = runTest {
        val nano = CountingRunner(LlmAvailability.Unsupported("no AICore"))
        val litert = CountingRunner(LlmAvailability.Ready)
        val provider = LlmRunnerProvider(
            candidates = mapOf(LlmBackend.NANO to nano, LlmBackend.LITERT to litert),
            preferredBackend = { LlmBackend.NANO },
            log = {},
        )

        val runner = provider.runner()

        assertTrue(runner.availability() is LlmAvailability.Unsupported)
        assertEquals(0, litert.probes)
    }
}
