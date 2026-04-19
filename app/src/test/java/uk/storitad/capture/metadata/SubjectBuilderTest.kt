package uk.storitad.capture.metadata

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectBuilderTest {
    private val defaultLabels = mapOf(
        "family" to "Family",
        "julian" to "Julian",
        "casper" to "Casper",
    )

    @Test
    fun `date plus default family recipient, no tags`() {
        val s = SubjectBuilder.build(
            capturedAt = Instant.parse("2026-04-19T20:13:00Z"),
            timezone = TimeZone.UTC,
            recipients = listOf("family"),
            recipientLabels = defaultLabels,
            tags = emptyList(),
        )
        assertEquals("19 Apr 2026 20:13 · Family", s)
    }

    @Test
    fun `includes tags after recipient in entry order`() {
        val s = SubjectBuilder.build(
            capturedAt = Instant.parse("2026-04-19T20:13:00Z"),
            timezone = TimeZone.UTC,
            recipients = listOf("family"),
            recipientLabels = defaultLabels,
            tags = listOf("cuddles", "bedtime"),
        )
        assertEquals("19 Apr 2026 20:13 · Family · cuddles · bedtime", s)
    }

    @Test
    fun `multi-recipient uses first recipient only`() {
        val s = SubjectBuilder.build(
            capturedAt = Instant.parse("2026-04-19T20:13:00Z"),
            timezone = TimeZone.UTC,
            recipients = listOf("julian", "family"),
            recipientLabels = defaultLabels,
            tags = listOf("cuddles"),
        )
        assertEquals("19 Apr 2026 20:13 · Julian · cuddles", s)
    }

    @Test
    fun `unknown recipient id falls back to the id string`() {
        val s = SubjectBuilder.build(
            capturedAt = Instant.parse("2026-04-19T20:13:00Z"),
            timezone = TimeZone.UTC,
            recipients = listOf("neighbour"),
            recipientLabels = defaultLabels,
            tags = emptyList(),
        )
        assertEquals("19 Apr 2026 20:13 · neighbour", s)
    }

    @Test
    fun `empty recipients omits the recipient segment`() {
        val s = SubjectBuilder.build(
            capturedAt = Instant.parse("2026-04-19T20:13:00Z"),
            timezone = TimeZone.UTC,
            recipients = emptyList(),
            recipientLabels = defaultLabels,
            tags = listOf("cuddles"),
        )
        assertEquals("19 Apr 2026 20:13 · cuddles", s)
    }

    @Test
    fun `renders local time using the supplied zone`() {
        val s = SubjectBuilder.build(
            capturedAt = Instant.parse("2026-04-19T23:30:00Z"),
            timezone = TimeZone.of("America/New_York"),
            recipients = listOf("family"),
            recipientLabels = defaultLabels,
            tags = emptyList(),
        )
        // 23:30 UTC on 2026-04-19 is 19:30 America/New_York (EDT, UTC-4).
        assertEquals("19 Apr 2026 19:30 · Family", s)
    }
}
