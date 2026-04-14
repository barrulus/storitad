package uk.storitad.capture.ui.drafts

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.io.File

object DraftHolder {
    data class Draft(
        val basename: String,
        val capturedAt: Instant,
        val timezone: TimeZone,
        val mediaFile: File,
        var durationMs: Long = 0
    )

    @Volatile private var current: Draft? = null

    fun begin(basename: String, at: Instant, zone: TimeZone, mediaFile: File) {
        current = Draft(basename, at, zone, mediaFile)
    }

    fun finalise(durationMs: Long) { current?.durationMs = durationMs }

    fun get(): Draft? = current

    fun clear() {
        current?.mediaFile?.takeIf { it.exists() }?.delete()
        current = null
    }

    fun consume() { current = null }
}
