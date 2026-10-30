package dev.andrej.echo.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.andrej.echo.data.db.EchoDatabase

fun echoDatabase(context: Context): EchoDatabase =
    EchoDatabase(AndroidSqliteDriver(EchoDatabase.Schema, context, "echo.db"))
