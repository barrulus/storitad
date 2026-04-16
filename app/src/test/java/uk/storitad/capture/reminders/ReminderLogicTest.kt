package uk.storitad.capture.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ReminderLogicTest {

    @get:Rule val tmp = TemporaryFolder()

    // --- computeInitialDelay ---

    @Test
    fun `target later today returns delay under 24h`() {
        val now = ZonedDateTime.of(2026, 4, 16, 18, 0, 0, 0, ZoneOffset.UTC)
        val delay = computeInitialDelay(now, LocalTime.of(20, 0))
        assertEquals(Duration.ofHours(2), delay)
    }

    @Test
    fun `target earlier today rolls to tomorrow`() {
        val now = ZonedDateTime.of(2026, 4, 16, 22, 0, 0, 0, ZoneOffset.UTC)
        val delay = computeInitialDelay(now, LocalTime.of(20, 0))
        assertEquals(Duration.ofHours(22), delay)
    }

    @Test
    fun `target equal to now rolls to tomorrow`() {
        val now = ZonedDateTime.of(2026, 4, 16, 20, 0, 0, 0, ZoneOffset.UTC)
        val delay = computeInitialDelay(now, LocalTime.of(20, 0))
        assertEquals(Duration.ofHours(24), delay)
    }

    @Test
    fun `midnight crossing`() {
        val now = ZonedDateTime.of(2026, 4, 16, 23, 30, 0, 0, ZoneOffset.UTC)
        val delay = computeInitialDelay(now, LocalTime.of(0, 15))
        assertEquals(Duration.ofMinutes(45), delay)
    }

    @Test
    fun `dst spring-forward still produces a sensible delay`() {
        // In US/Eastern on 2026-03-08, clocks jump from 02:00 to 03:00.
        val zone = java.time.ZoneId.of("America/New_York")
        val now = java.time.ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, zone)
        val delay = computeInitialDelay(now, java.time.LocalTime.of(20, 0))
        // Wall-clock 01:30 → 20:00 same day = 18.5h of wall time, but because
        // 02:00–03:00 is skipped, elapsed duration is 17.5h.
        assertEquals(java.time.Duration.ofMinutes(17 * 60 + 30), delay)
    }

    // --- hasFilenameForDate ---

    @Test
    fun `hasFilenameForDate matches today prefix`() {
        val today = LocalDate.of(2026, 4, 16)
        val files = listOf(
            tmp.newFile("20260416-093000-voice.m4a"),
            tmp.newFile("20260415-200000-voice.m4a")
        )
        assertTrue(hasFilenameForDate(files, today))
    }

    @Test
    fun `hasFilenameForDate no match when nothing today`() {
        val today = LocalDate.of(2026, 4, 16)
        val files = listOf(
            tmp.newFile("20260415-200000-voice.m4a"),
            tmp.newFile("20260414-093000-video.mp4")
        )
        assertFalse(hasFilenameForDate(files, today))
    }

    @Test
    fun `hasFilenameForDate ignores sidecar json`() {
        val today = LocalDate.of(2026, 4, 16)
        val files = listOf(
            tmp.newFile("20260416-093000-voice.json"),
            tmp.newFile("20260416-093000-voice.m4a")
        )
        assertTrue(hasFilenameForDate(files, today))  // either hit is fine
    }

    // --- hasRecordingSince ---

    @Test
    fun `hasRecordingSince true when file newer than cutoff`() {
        val f = tmp.newFile("recent.m4a").apply { setLastModified(1_000_000L) }
        assertTrue(hasRecordingSince(listOf(f), cutoffMillis = 500_000L))
    }

    @Test
    fun `hasRecordingSince false when file older than cutoff`() {
        val f = tmp.newFile("old.m4a").apply { setLastModified(500_000L) }
        assertFalse(hasRecordingSince(listOf(f), cutoffMillis = 1_000_000L))
    }

    @Test
    fun `hasRecordingSince empty list returns false`() {
        assertFalse(hasRecordingSince(emptyList(), cutoffMillis = 0L))
    }
}
