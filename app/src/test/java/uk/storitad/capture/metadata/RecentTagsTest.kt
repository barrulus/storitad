package uk.storitad.capture.metadata

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentTagsTest {

    @Test
    fun `bump with empty new tags returns existing trimmed to cap`() {
        val input = List(35) { "t$it" }
        val out = RecentTags.bump(existing = input, newTags = emptyList(), cap = 30)
        assertEquals(30, out.size)
        assertEquals("t0", out.first())
    }

    @Test
    fun `bump moves new tags to the head in the given order`() {
        val out = RecentTags.bump(
            existing = listOf("old1", "old2"),
            newTags = listOf("new1", "new2"),
            cap = 30,
        )
        assertEquals(listOf("new1", "new2", "old1", "old2"), out)
    }

    @Test
    fun `bump deduplicates case-insensitively, preserving first-seen casing`() {
        val out = RecentTags.bump(
            existing = listOf("Bedtime", "cuddles"),
            newTags = listOf("BEDTIME", "school"),
            cap = 30,
        )
        assertEquals(listOf("BEDTIME", "school", "cuddles"), out)
    }

    @Test
    fun `bump drops blank and whitespace-only tags`() {
        val out = RecentTags.bump(
            existing = emptyList(),
            newTags = listOf("  ", "", "bedtime", "  cuddles  "),
            cap = 30,
        )
        assertEquals(listOf("bedtime", "cuddles"), out)
    }

    @Test
    fun `bump enforces the cap`() {
        val out = RecentTags.bump(
            existing = List(29) { "t$it" },
            newTags = listOf("new1", "new2", "new3"),
            cap = 30,
        )
        assertEquals(30, out.size)
        assertEquals("new1", out.first())
        assertEquals("t26", out.last())
    }

    @Test
    fun `seedFromEntries orders newest tags first`() {
        val out = RecentTags.seedFromEntries(
            entries = listOf(
                Instant.parse("2026-04-10T10:00:00Z") to listOf("old"),
                Instant.parse("2026-04-19T10:00:00Z") to listOf("new", "shared"),
                Instant.parse("2026-04-15T10:00:00Z") to listOf("mid", "shared"),
            ),
            cap = 30,
        )
        assertEquals(listOf("new", "shared", "mid", "old"), out)
    }

    @Test
    fun `seedFromEntries honours cap`() {
        val entries = (1..40).map {
            Instant.parse("2026-04-19T10:00:00Z") to listOf("t$it")
        }
        val out = RecentTags.seedFromEntries(entries, cap = 30)
        assertEquals(30, out.size)
    }
}
