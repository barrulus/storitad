package uk.storitad.capture.storage

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class FileManagerTest {
    @Test
    fun `basename formats timestamp in local zone`() {
        val at = Instant.parse("2026-04-14T15:32:00Z")
        val zone = TimeZone.of("Europe/London")
        assertEquals("20260414-163200-voice", FileManager.basename(at, zone, "voice"))
    }

    @Test
    fun `media and sidecar filenames share basename`() {
        val at = Instant.parse("2026-04-14T15:32:00Z")
        val zone = TimeZone.UTC
        val basename = FileManager.basename(at, zone, "voice")
        assertEquals("$basename.m4a", FileManager.mediaFilename(basename, "m4a"))
        assertEquals("$basename.json", FileManager.sidecarFilename(basename))
    }
}
