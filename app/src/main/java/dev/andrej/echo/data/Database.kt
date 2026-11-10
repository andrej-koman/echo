package dev.andrej.echo.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.andrej.echo.data.db.EchoDatabase

const val DATABASE_NAME = "echo.db"

fun echoDriver(context: Context): AndroidSqliteDriver =
    AndroidSqliteDriver(EchoDatabase.Schema, context, DATABASE_NAME)

/** Read-only, so a staged backup's version can be checked without migrating it. */
fun schemaVersionOf(file: java.io.File): Long =
    android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        .use { it.version.toLong() }
