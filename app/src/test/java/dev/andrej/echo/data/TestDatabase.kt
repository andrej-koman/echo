package dev.andrej.echo.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.andrej.echo.data.db.EchoDatabase

fun testDatabase(): EchoDatabase =
    EchoDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, schema = EchoDatabase.Schema))
