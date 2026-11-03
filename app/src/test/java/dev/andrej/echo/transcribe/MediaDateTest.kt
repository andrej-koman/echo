package dev.andrej.echo.transcribe

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaDateTest {
    private val zone = ZoneId.of("Europe/Ljubljana")

    @Test
    fun utcStamp() {
        assertEquals(
            ZonedDateTime.of(2026, 10, 28, 10, 31, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli(),
            parseMediaDate("20261028T103100.000Z", zone),
        )
    }

    @Test
    fun stampWithoutZoneIsLocal() {
        assertEquals(
            ZonedDateTime.of(2026, 10, 28, 10, 31, 0, 0, zone).toInstant().toEpochMilli(),
            parseMediaDate("20261028T103100", zone),
        )
    }

    @Test
    fun mp4EpochMeansNoDate() = assertNull(parseMediaDate("19040101T000000.000Z", zone))

    @Test
    fun garbageIsNoDate() = assertNull(parseMediaDate("yesterday", zone))
}
