package uk.storitad.capture.capture

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0
    private var pausedAtMs: Long = 0
    private var pausedAccumMs: Long = 0
    private var isPaused: Boolean = false

    private val _amplitudes = MutableStateFlow(FloatArray(AMP_RING))
    val amplitudes: StateFlow<FloatArray> = _amplitudes

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    fun start(targetFile: File) {
        require(recorder == null) { "recorder already running" }
        outputFile = targetFile
        pausedAccumMs = 0
        isPaused = false
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
        pollJob = scope.launch { pollAmplitudes() }
    }

    fun pause() {
        val r = recorder ?: return
        if (isPaused) return
        r.pause()
        pausedAtMs = System.currentTimeMillis()
        isPaused = true
    }

    fun resume() {
        val r = recorder ?: return
        if (!isPaused) return
        r.resume()
        pausedAccumMs += System.currentTimeMillis() - pausedAtMs
        isPaused = false
    }

    fun isPaused(): Boolean = isPaused

    fun elapsedMs(): Long {
        if (recorder == null) return 0
        val now = System.currentTimeMillis()
        val raw = now - startedAtMs - pausedAccumMs
        return if (isPaused) now - startedAtMs - pausedAccumMs - (now - pausedAtMs)
               else raw
    }

    fun stop(): Long {
        val r = recorder ?: error("recorder not running")
        return try {
            if (isPaused) r.resume()
            r.stop()
            System.currentTimeMillis() - startedAtMs - pausedAccumMs
        } finally {
            pollJob?.cancel(); pollJob = null
            r.release()
            recorder = null
            isPaused = false
        }
    }

    fun cancel() {
        val r = recorder ?: return
        pollJob?.cancel(); pollJob = null
        runCatching { r.stop() }
        r.release()
        recorder = null
        isPaused = false
        outputFile?.delete()
        outputFile = null
    }

    private suspend fun pollAmplitudes() {
        val ring = FloatArray(AMP_RING)
        var i = 0
        while (scope.isActive && recorder != null) {
            if (!isPaused) {
                val r = recorder
                val max = runCatching { r?.maxAmplitude ?: 0 }.getOrDefault(0)
                ring[i] = (max.coerceAtMost(32767) / 32767f)
                i = (i + 1) % ring.size
                _amplitudes.value = ring.copyOf()
            }
            delay(50)
        }
    }

    companion object { const val AMP_RING = 40 }
}
