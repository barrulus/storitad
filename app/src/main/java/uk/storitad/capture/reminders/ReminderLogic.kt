package uk.storitad.capture.reminders

import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

fun computeInitialDelay(now: ZonedDateTime, target: LocalTime): Duration {
    val todayTarget = now.with(target).withSecond(0).withNano(0)
    val next = if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
    return Duration.between(now, next)
}

fun hasFilenameForDate(files: List<File>, date: LocalDate): Boolean {
    val prefix = "%04d%02d%02d".format(date.year, date.monthValue, date.dayOfMonth)
    return files.any { it.name.startsWith(prefix) }
}

fun hasRecordingSince(files: List<File>, cutoffMillis: Long): Boolean =
    files.any { it.lastModified() >= cutoffMillis }
