package uk.storitad.capture.capture

import android.media.MediaRecorder
import android.util.Size

private const val TARGET_W = 1280
private const val TARGET_H = 720
private const val TARGET_AREA: Long = TARGET_W.toLong() * TARGET_H

/**
 * Pick the best 720p match from a camera's supported recorder sizes.
 *
 * Preference order:
 *   1. Exact 1280×720
 *   2. Largest size with area ≤ 720p
 *   3. Smallest size with area > 720p (only if nothing fits below)
 *   4. null if the list is empty
 */
fun pickVideoSize(candidates: List<Size>): Size? {
    val pairs = candidates.map { it.width to it.height }
    val pick = pickVideoSizeFrom(pairs) ?: return null
    return Size(pick.first, pick.second)
}

/**
 * Pure-JVM testable core. Operates on (width, height) pairs so tests
 * don't have to depend on `android.util.Size`, which is unmockable
 * without Robolectric.
 */
internal fun pickVideoSizeFrom(candidates: List<Pair<Int, Int>>): Pair<Int, Int>? {
    if (candidates.isEmpty()) return null
    candidates.firstOrNull { it.first == TARGET_W && it.second == TARGET_H }?.let { return it }
    val below = candidates.filter { it.first.toLong() * it.second <= TARGET_AREA }
    if (below.isNotEmpty()) return below.maxByOrNull { it.first.toLong() * it.second }
    return candidates.minByOrNull { it.first.toLong() * it.second }
}

/**
 * Pick the best audio source for recording.
 *
 * Preference: UNPROCESSED if the device reports support, else MIC.
 *
 * The caller is expected to query device support via:
 * ```
 * audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
 * ```
 *
 * @param unprocessedSupported Boolean indicating device support for UNPROCESSED source
 * @return MediaRecorder.AudioSource.UNPROCESSED or MediaRecorder.AudioSource.MIC
 */
fun pickAudioSource(unprocessedSupported: Boolean): Int =
    if (unprocessedSupported) MediaRecorder.AudioSource.UNPROCESSED
    else MediaRecorder.AudioSource.MIC
