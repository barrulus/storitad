package uk.storitad.capture.capture

sealed interface CaptureState {
    data object Idle : CaptureState
    data class Recording(val startedAtMs: Long) : CaptureState
    data class Stopped(val outputPath: String, val durationMs: Long) : CaptureState
    data class Failed(val message: String) : CaptureState
}
