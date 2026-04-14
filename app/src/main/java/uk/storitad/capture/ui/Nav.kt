package uk.storitad.capture.ui

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Recording : Route("recording")
    data object Review : Route("review/{basename}") {
        fun of(basename: String) = "review/$basename"
    }
    data object Metadata : Route("metadata/{basename}?edit={edit}") {
        fun of(basename: String, edit: Boolean = false) = "metadata/$basename?edit=$edit"
    }
    data object Pending : Route("pending")
    data object History : Route("history")
    data object Detail : Route("detail/{basename}") {
        fun of(basename: String) = "detail/$basename"
    }
}
