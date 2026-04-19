package uk.storitad.capture.ui

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Recording : Route("recording")
    data object VideoRecording : Route("video-recording")
    data object Commit : Route("commit/{basename}") {
        fun of(basename: String) = "commit/$basename"
    }
    data object Metadata : Route("metadata/{basename}") {
        fun of(basename: String) = "metadata/$basename"
    }
    data object Pending : Route("pending")
    data object History : Route("history")
    data object Detail : Route("detail/{basename}") {
        fun of(basename: String) = "detail/$basename"
    }
    data object Settings : Route("settings")
}
