package dev.andrej.echo.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.andrej.echo.data.db.EchoDatabase
import org.junit.Assert.*
import org.junit.Test

class TaskReminderMigrationTest {
    @Test fun `upgrade preserves existing tasks without scheduling old reminders`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            driver.execute(null, """
                CREATE TABLE todo (
                    id TEXT NOT NULL PRIMARY KEY, sourceTranscriptId TEXT NOT NULL, text TEXT NOT NULL,
                    dueAt INTEGER NOT NULL, hasTime INTEGER NOT NULL, hasDate INTEGER NOT NULL,
                    done INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    sourceOffsetMs INTEGER
                )
            """.trimIndent(), 0)
            driver.execute(null, "INSERT INTO todo VALUES ('old', 'note', 'Old task', 0, 0, 1, 0, 0, 0, NULL)", 0)
            EchoDatabase.Schema.migrate(driver, 6, 7)
            // This fixture has only todo; migration 7 changes transcript.
            EchoDatabase.Schema.migrate(driver, 8, 9)
            val task = EchoDatabase(driver).todoQueries.selectAll(::TodoItem).executeAsOne()
            assertEquals("Old task", task.text)
            assertNull(task.reminderAt)
            assertNull(task.reminderDate)
            assertFalse(task.reminderDelivered)
            assertNull(task.sourceQuote)
        }
    }
}
