package dev.andrej.echo.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Deletes used to tombstone rows (`deletedAt`) for sync; they are hard deletes now. Files written
 * before that still hold the tombstoned rows, which would resurrect once the field stopped being
 * read — so they are dropped on load, and the next write removes them from disk.
 */
internal fun JsonArray.withoutTombstones(): JsonArray =
    JsonArray(filter { row -> (row as? JsonObject)?.get("deletedAt").let { it == null || it is JsonNull } })
