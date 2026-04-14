package uk.storitad.capture.storage

import android.content.Context
import android.os.Environment
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File

object FileManager {
    fun basename(at: Instant, zone: TimeZone, kind: String): String {
        val ldt = at.toLocalDateTime(zone)
        val date = "%04d%02d%02d".format(ldt.year, ldt.monthNumber, ldt.dayOfMonth)
        val time = "%02d%02d%02d".format(ldt.hour, ldt.minute, ldt.second)
        return "$date-$time-$kind"
    }

    fun mediaFilename(basename: String, ext: String) = "$basename.$ext"
    fun sidecarFilename(basename: String) = "$basename.json"

    fun inboxDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: error("external files dir unavailable")
        return File(base, "Storitad/inbox").apply { mkdirs() }
    }

    const val INBOX_RELATIVE_PATH = "Documents/Storitad/inbox"
}
