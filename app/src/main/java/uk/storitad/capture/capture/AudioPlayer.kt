package uk.storitad.capture.capture

import android.media.MediaPlayer
import java.io.File

class AudioPlayer {
    private var player: MediaPlayer? = null

    fun play(file: File, onCompletion: () -> Unit) {
        stop()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { onCompletion() }
            prepare()
            start()
        }
    }

    fun stop() {
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            it.release()
        }
        player = null
    }
}
