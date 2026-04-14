package uk.storitad.capture.metadata

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryMetadataTest {
    private val json = Json { prettyPrint = false; encodeDefaults = true }

    @Test
    fun `round trips through JSON preserving all fields`() {
        val original = EntryMetadata(
            id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            capturedAt = Instant.parse("2026-04-14T15:32:00Z"),
            durationSeconds = 154L,
            timezone = "Europe/London",
            mediaFile = "20260414-153200-voice.m4a",
            mediaType = MediaType.VOICE,
            mimeType = "audio/mp4",
            subject = "Picking Casper up from school",
            recipients = listOf("casper"),
            mood = "happy",
            tags = listOf("school", "daily"),
            notes = "He beat the level",
            location = GeoFix(
                latitude = 51.5074,
                longitude = -0.1278,
                accuracyMeters = 12.4f,
                capturedAt = Instant.parse("2026-04-14T15:32:10Z")
            ),
            device = "Pixel 9 Pro",
            appVersion = "0.1.0"
        )
        val encoded = json.encodeToString(EntryMetadata.serializer(), original)
        val decoded = json.decodeFromString(EntryMetadata.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `defaults apply when optional fields absent`() {
        val raw = """
            {"id":"x","version":2,"capturedAt":"2026-04-14T15:32:00Z",
             "durationSeconds":10,"timezone":"UTC","mediaFile":"x.m4a",
             "mediaType":"VOICE","mimeType":"audio/mp4","subject":"s",
             "device":"d","appVersion":"v"}
        """.trimIndent()
        val decoded = json.decodeFromString(EntryMetadata.serializer(), raw)
        assertEquals(listOf("family"), decoded.recipients)
        assertEquals(emptyList<String>(), decoded.tags)
        assertEquals(null, decoded.location)
        assertEquals(false, decoded.processed)
    }

    @Test
    fun `v1 sidecar without location fields still decodes`() {
        val raw = """
            {"id":"x","version":1,"capturedAt":"2026-04-14T15:32:00Z",
             "durationSeconds":10,"timezone":"UTC","mediaFile":"x.m4a",
             "mediaType":"VOICE","mimeType":"audio/mp4","subject":"s",
             "device":"d","appVersion":"v"}
        """.trimIndent()
        val decoded = json.decodeFromString(EntryMetadata.serializer(), raw)
        assertEquals(1, decoded.version)
        assertEquals(null, decoded.location)
    }
}
