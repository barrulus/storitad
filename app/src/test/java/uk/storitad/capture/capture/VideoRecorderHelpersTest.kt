package uk.storitad.capture.capture

import org.junit.Test
import org.junit.Assert.assertEquals

class VideoRecorderHelpersTest {

    @Test fun `prefers exact 1280x720 when present`() {
        val candidates = listOf(1920 to 1080, 1280 to 720, 640 to 480)
        assertEquals(1280 to 720, pickVideoSizeFrom(candidates))
    }

    @Test fun `falls back to largest below 720p when exact missing`() {
        val candidates = listOf(1920 to 1080, 960 to 540, 640 to 480)
        assertEquals(960 to 540, pickVideoSizeFrom(candidates))
    }

    @Test fun `falls back to smallest above 720p when nothing below`() {
        val candidates = listOf(3840 to 2160, 1920 to 1080)
        assertEquals(1920 to 1080, pickVideoSizeFrom(candidates))
    }

    @Test fun `returns null on empty list`() {
        assertEquals(null, pickVideoSizeFrom(emptyList()))
    }
}
