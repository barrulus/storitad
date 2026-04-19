package uk.storitad.capture.ui.drafts

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import uk.storitad.capture.metadata.GeoFix
import java.io.File

object DraftHolder {
    data class Draft(
        val basename: String,
        val capturedAt: Instant,
        val timezone: TimeZone,
        val mediaFile: File,
        var durationMs: Long = 0,
        var locationFix: GeoFix? = null,
    )

    @Volatile private var current: Draft? = null

    fun begin(basename: String, at: Instant, zone: TimeZone, mediaFile: File) {
        current = Draft(basename, at, zone, mediaFile)
    }

    fun finalise(durationMs: Long) { current?.durationMs = durationMs }

    fun setLocationFix(fix: GeoFix) { current?.locationFix = fix }

    fun get(): Draft? = current

    fun clear() {
        DraftLocationJob.cancel()
        current?.mediaFile?.takeIf { it.exists() }?.delete()
        current = null
    }

    fun consume() {
        DraftLocationJob.cancel()
        current = null
    }
}
