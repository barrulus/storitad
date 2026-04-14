package uk.storitad.capture.metadata

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class MediaType { VOICE, VIDEO }

@Serializable
data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val capturedAt: Instant
)

@Serializable
data class EntryMetadata(
    val id: String,
    val version: Int = 2,
    val capturedAt: Instant,
    val durationSeconds: Long,
    val timezone: String,
    val mediaFile: String,
    val mediaType: MediaType,
    val mimeType: String,
    val subject: String,
    val recipients: List<String> = listOf("family"),
    val mood: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val location: GeoFix? = null,
    val locationName: String? = null,
    val device: String,
    val appVersion: String,
    val transcriptDraft: String? = null,
    val transcriptModel: String? = null,
    val processed: Boolean = false
) {
    val duration: Duration get() = durationSeconds.seconds
}
