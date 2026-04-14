package uk.storitad.capture.metadata

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MetadataRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun sample(basename: String) = EntryMetadata(
        id = basename,
        capturedAt = Instant.parse("2026-04-14T15:32:00Z"),
        durationSeconds = 10,
        timezone = "UTC",
        mediaFile = "$basename.m4a",
        mediaType = MediaType.VOICE,
        mimeType = "audio/mp4",
        subject = "s",
        device = "d",
        appVersion = "v"
    )

    @Test
    fun `write then read round trips`() {
        val repo = MetadataRepository(tmp.root)
        val meta = sample("20260414-153200-voice")
        repo.write(meta)
        val read = repo.read("20260414-153200-voice")
        assertEquals(meta, read)
    }

    @Test
    fun `list returns all entries sorted newest first`() {
        val repo = MetadataRepository(tmp.root)
        repo.write(
            sample("20260413-100000-voice")
                .copy(capturedAt = Instant.parse("2026-04-13T10:00:00Z"))
        )
        repo.write(sample("20260414-153200-voice"))
        val all = repo.list()
        assertEquals(
            listOf("20260414-153200-voice", "20260413-100000-voice"),
            all.map { it.id }
        )
    }

    @Test
    fun `list ignores non-json files`() {
        val repo = MetadataRepository(tmp.root)
        tmp.newFile("stray.m4a")
        repo.write(sample("20260414-153200-voice"))
        assertEquals(1, repo.list().size)
    }

    @Test
    fun `listPending filters processed entries`() {
        val repo = MetadataRepository(tmp.root)
        repo.write(sample("a-voice"))
        repo.write(sample("b-voice").copy(processed = true))
        val pending = repo.listPending().map { it.id }
        assertEquals(listOf("a-voice"), pending)
    }
}
