package dev.andrej.echo.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.andrej.echo.data.db.EchoDatabase
import org.junit.Assert.*
import org.junit.Test

class TaskReminderMigrationTest {
    @Test fun `upgrade preserves existing tasks without scheduling old reminders`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            driver.execute(null, "CREATE TABLE transcript (id TEXT NOT NULL PRIMARY KEY)", 0)
            driver.execute(null, """
                CREATE TABLE todo (
                    id TEXT NOT NULL PRIMARY KEY, sourceTranscriptId TEXT NOT NULL, text TEXT NOT NULL,
                    dueAt INTEGER NOT NULL, hasTime INTEGER NOT NULL, hasDate INTEGER NOT NULL,
                    done INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    sourceOffsetMs INTEGER
                )
            """.trimIndent(), 0)
            driver.execute(null, "INSERT INTO todo VALUES ('old', 'note', 'Old task', 0, 0, 1, 0, 0, 0, NULL)", 0)
            EchoDatabase.Schema.migrate(driver, 6, EchoDatabase.Schema.version)
            val task = EchoDatabase(driver).todoQueries.selectAll(::TodoItem).executeAsOne()
            assertEquals("Old task", task.text)
            assertNull(task.reminderAt)
            assertNull(task.reminderDate)
            assertFalse(task.reminderDelivered)
            assertNull(task.sourceQuote)
        }
    }

    @Test fun `upgrade from transcription cap schema adds source quote and preserves reminders`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            driver.execute(null, "CREATE TABLE transcript (id TEXT NOT NULL PRIMARY KEY)", 0)
            driver.execute(null, """
                CREATE TABLE todo (
                    id TEXT NOT NULL PRIMARY KEY, sourceTranscriptId TEXT NOT NULL, text TEXT NOT NULL,
                    dueAt INTEGER NOT NULL, hasTime INTEGER NOT NULL, hasDate INTEGER NOT NULL,
                    done INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    sourceOffsetMs INTEGER, reminderAt INTEGER, reminderDate TEXT,
                    reminderDelivered INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(), 0)
            driver.execute(null, """
                INSERT INTO todo VALUES (
                    'old', 'note', 'Old task', 0, 0, 1, 0, 0, 0, 500, 1000, '2026-11-09', 1
                )
            """.trimIndent(), 0)
            EchoDatabase.Schema.migrate(driver, 8, EchoDatabase.Schema.version)
            val task = EchoDatabase(driver).todoQueries.selectAll(::TodoItem).executeAsOne()
            assertEquals("Old task", task.text)
            assertEquals(500L, task.sourceOffsetMs)
            assertEquals(1000L, task.reminderAt)
            assertEquals("2026-11-09", task.reminderDate)
            assertTrue(task.reminderDelivered)
            assertNull(task.sourceQuote)
        }
    }

    @Test fun `language upgrade marks checked tasks touched and leaves notes following settings`() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            driver.execute(null, "CREATE TABLE transcript (id TEXT NOT NULL PRIMARY KEY)", 0)
            driver.execute(null, """
                CREATE TABLE todo (
                    id TEXT NOT NULL PRIMARY KEY, sourceTranscriptId TEXT NOT NULL, text TEXT NOT NULL,
                    dueAt INTEGER NOT NULL, hasTime INTEGER NOT NULL, hasDate INTEGER NOT NULL,
                    done INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    sourceOffsetMs INTEGER, reminderAt INTEGER, reminderDate TEXT,
                    reminderDelivered INTEGER NOT NULL DEFAULT 0, sourceQuote TEXT
                )
            """.trimIndent(), 0)
            driver.execute(null, "INSERT INTO transcript VALUES ('note')", 0)
            driver.execute(null, "INSERT INTO todo VALUES ('done', 'note', 'Done', 0, 0, 1, 1, 0, 0, NULL, NULL, NULL, 0, NULL)", 0)
            driver.execute(null, "INSERT INTO todo VALUES ('open', 'note', 'Open', 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 0, NULL)", 0)
            EchoDatabase.Schema.migrate(driver, 9, EchoDatabase.Schema.version)
            val tasks = EchoDatabase(driver).todoQueries.selectAll(::TodoItem).executeAsList().associateBy { it.id }
            assertTrue(tasks.getValue("done").touched)
            assertFalse(tasks.getValue("open").touched)
            val override = driver.executeQuery(null, "SELECT languageOverride FROM transcript", { cursor ->
                cursor.next()
                app.cash.sqldelight.db.QueryResult.Value(cursor.getString(0))
            }, 0).value
            assertNull(override)
        }
    }
}
