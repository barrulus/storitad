package uk.storitad.capture.metadata

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toJavaZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object SubjectBuilder {
    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH)

    private const val SEP = " · "

    fun build(
        capturedAt: Instant,
        timezone: TimeZone,
        recipients: List<String>,
        recipientLabels: Map<String, String>,
        tags: List<String>,
    ): String {
        val when_ = capturedAt.toJavaInstant()
            .atZone(timezone.toJavaZoneId())
            .toLocalDateTime()
            .format(FORMATTER)
        val parts = buildList {
            add(when_)
            recipients.firstOrNull()?.let { id ->
                add(recipientLabels[id] ?: id)
            }
            addAll(tags)
        }
        return parts.joinToString(SEP)
    }
}
