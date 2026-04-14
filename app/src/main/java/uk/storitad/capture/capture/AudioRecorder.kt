package uk.storitad.capture.capture

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0

    fun start(targetFile: File) {
        require(recorder == null) { "recorder already running" }
        outputFile = targetFile
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(44100)
        r.setAudioEncodingBitRate(128_000)
        r.setOutputFile(targetFile.absolutePath)
        r.prepare()
        r.start()
        startedAtMs = System.currentTimeMillis()
        recorder = r
    }

    fun stop(): Long {
        val r = recorder ?: error("recorder not running")
        return try {
            r.stop()
            System.currentTimeMillis() - startedAtMs
        } finally {
            r.release()
            recorder = null
        }
    }

    fun cancel() {
        val r = recorder ?: return
        runCatching { r.stop() }
        r.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
