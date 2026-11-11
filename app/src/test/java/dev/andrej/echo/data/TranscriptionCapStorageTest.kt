package dev.andrej.echo.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.andrej.echo.data.db.EchoDatabase
import dev.andrej.echo.transcribe.FREE_SAMPLES
import dev.andrej.echo.transcribe.TranscriptionCheckpoint
import dev.andrej.echo.transcribe.resumeChunk
import dev.andrej.echo.transcribe.preservePrefixSpeakers
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TranscriptionCapStorageTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `cap survives chunk and analysis writes and clears explicitly`() = runTest {
        val repository = SqlTranscriptRepository(testDatabase(), StandardTestDispatcher(testScheduler))
        repository.save("note", "first", "en", 300_000)
        repository.setTranscribedUntil("note", 120_000)
        repository.setTranscription("note", "first next", "en", 300_000, listOf(Segment(0, 1000, "first next")))
        repository.attachAnalysis("note", "title", "summary")
        assertEquals(120_000L, repository.transcripts.first().single().transcribedUntilMs)
        repository.setTranscribedUntil("note", null)
        assertNull(repository.transcripts.first().single().transcribedUntilMs)
    }

    @Test fun `unlock restores database prefix then recovers a chunk newer than database after death`() = runTest {
        val repository = SqlTranscriptRepository(testDatabase(), StandardTestDispatcher(testScheduler))
        val prefix = listOf(Segment(0, 1000, "prefix"))
        repository.save("note", "prefix", "sl", 600_000)
        repository.setTranscription("note", "prefix", "sl", 600_000, prefix)
        repository.setTranscribedUntil("note", 120_000)
        val stored = repository.transcripts.first().single()
        val resumed = resumeChunk(null, stored, repository.segments("note"))!!
        assertEquals(FREE_SAMPLES.toLong(), resumed.nextSample)
        val appended = resumed.segments + Segment(120_000, 121_000, "rest")
        val checkpoint = TranscriptionCheckpoint(folder.root.resolve("speech-complete"))
        checkpoint.saveChunk(FREE_SAMPLES + 16000L, "sl", appended)
        // The process dies after the file sync, before the repository's per-chunk write.
        val afterDeath = resumeChunk(checkpoint.chunk(), stored, repository.segments("note"))!!
        repository.setTranscription("note", afterDeath.segments.joinToString(" ") { it.text }, "sl", 600_000, afterDeath.segments)
        assertEquals(appended, repository.segments("note"))
        assertEquals(120_000L, repository.transcripts.first().single().transcribedUntilMs)
        repository.setTranscribedUntil("note", null)
        assertNull(repository.transcripts.first().single().transcribedUntilMs)
    }

    @Test fun `append preserves edited prefix and speaker names and allocates new speaker ids`() = runTest {
        val repository = SqlTranscriptRepository(testDatabase(), StandardTestDispatcher(testScheduler))
        repository.save("note", "edited prefix", "en", 300000)
        val prefix = listOf(Segment(0, 1000, "edited prefix", 1))
        repository.setTranscription("note", "edited prefix", "en", 300000, prefix)
        repository.setSpeakers("note", prefix, 2)
        repository.renameSpeaker("note", 1, "Andrej")
        repository.setTranscribedUntil("note", 120000)
        val stale = listOf(Segment(0, 1000, "old text"), Segment(120000, 121000, "new person"))
        val chunk = TranscriptionCheckpoint.Chunk(FREE_SAMPLES + 16000L, "en", stale)
        val resumed = resumeChunk(chunk, repository.transcripts.first().single(), repository.segments("note"))!!
        assertEquals(prefix.single(), resumed.segments.first())
        repository.setTranscription("note", "edited prefix new person", "en", 300000, resumed.segments, preserveSpeakers = true)
        assertEquals("Andrej", repository.speakers("note").first()[1].name)
        val detected = resumed.segments.mapIndexed { i, segment -> segment.copy(speaker = i) }
        val assigned = preservePrefixSpeakers(detected, prefix, 2)
        assertEquals(1, assigned.first().speaker)
        assertEquals(2, assigned.last().speaker)
        repository.setSpeakers("note", assigned, 3, preserveExisting = true)
        assertEquals("Andrej", repository.speakers("note").first()[1].name)
        assertEquals(listOf(0, 1, 2), repository.speakers("note").first().map { it.index })
        assertEquals(prefix.single(), repository.segments("note").first())
        // Full speech committed, then process death before speech-complete was created.
        repository.completeTranscription("note", null, analyze = true)
        val marker = folder.root.resolve("speech-complete")
        val checkpoint = TranscriptionCheckpoint(marker)
        checkpoint.saveChunk(chunk.nextSample, "en", stale)
        assertFalse(marker.exists())
        val completed = resumeChunk(checkpoint.chunk(), repository.transcripts.first().single(),
            repository.segments("note"), preserveCompleted = true)!!
        assertEquals(chunk.nextSample, completed.nextSample)
        assertEquals(assigned, completed.segments)
        repository.setTranscription("note", "edited prefix new person", "en", 300000,
            completed.segments, preserveSpeakers = true)
        assertEquals("Andrej", repository.speakers("note").first()[1].name)
        assertEquals(assigned, repository.segments("note"))
    }

    @Test fun `full completion invalidates old analysis without queueing when auto analysis is off`() = runTest {
        val database = testDatabase()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = SqlTranscriptRepository(database, dispatcher)
        val queue = SqlAnalysisQueueRepository(database, dispatcher)
        repository.save("note", "prefix", "en", 300000)
        repository.setTranscribedUntil("note", 120000)
        repository.attachAnalysis("note", "old", "old", 10)
        queue.enqueue("note")
        repository.completeTranscription("note", null, analyze = false)
        assertNull(repository.transcripts.first().single().analyzedAt)
        assertNull(repository.transcripts.first().single().transcribedUntilMs)
        assertTrue(queue.pending.first().isEmpty())
    }

    @Test fun `migration preserves old transcripts as fully transcribed`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            driver.execute(null, """
                CREATE TABLE transcript (
                    id TEXT NOT NULL PRIMARY KEY, text TEXT NOT NULL, language TEXT NOT NULL,
                    createdAt INTEGER NOT NULL, durationMs INTEGER NOT NULL, title TEXT,
                    summary TEXT, analyzedAt INTEGER, polishedText TEXT, folderId TEXT,
                    titleByUser INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(), 0)
            driver.execute(null, "CREATE TABLE todo (id TEXT NOT NULL PRIMARY KEY, done INTEGER NOT NULL)", 0)
            driver.execute(null, "INSERT INTO transcript (id, text, language, createdAt, durationMs) VALUES ('old', 'spoken', 'sl', 10, 300000)", 0)
            EchoDatabase.Schema.migrate(driver, 7, EchoDatabase.Schema.version)
            val note = EchoDatabase(driver).transcriptQueries.selectAll(::Transcript).executeAsOne()
            assertEquals("spoken", note.text)
            assertEquals(300_000L, note.durationMs)
            assertNull(note.transcribedUntilMs)
            assertNull(note.languageOverride)
        }
    }
}
