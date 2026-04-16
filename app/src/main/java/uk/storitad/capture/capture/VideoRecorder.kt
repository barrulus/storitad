package uk.storitad.capture.capture

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class VideoRecorder(private val context: Context) {
    private var provider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var active: Recording? = null
    private var camera: Camera? = null
    private var startedAtMs: Long = 0
    private var finalDurationMs: Long = 0

    var useFrontCamera: Boolean = true

    suspend fun bind(owner: LifecycleOwner, preview: Preview) {
        val p = awaitProvider()
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        val vc = VideoCapture.withOutput(recorder)
        videoCapture = vc
        val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                       else CameraSelector.DEFAULT_BACK_CAMERA
        p.unbindAll()
        camera = p.bindToLifecycle(owner, selector, preview, vc)
        provider = p
    }

    fun pinchZoom(factor: Float) {
        val cam = camera ?: return
        val state = cam.cameraInfo.zoomState.value ?: return
        val newRatio = (state.zoomRatio * factor)
            .coerceIn(state.minZoomRatio, state.maxZoomRatio)
        cam.cameraControl.setZoomRatio(newRatio)
    }

    @SuppressLint("MissingPermission")
    fun start(target: File, onFinalised: (Long) -> Unit) {
        val vc = videoCapture ?: error("not bound")
        val opts = FileOutputOptions.Builder(target).build()
        val executor = ContextCompat.getMainExecutor(context)
        startedAtMs = System.currentTimeMillis()
        active = vc.output
            .prepareRecording(context, opts)
            .withAudioEnabled()
            .start(executor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    finalDurationMs = System.currentTimeMillis() - startedAtMs
                    if (event.hasError()) {
                        Log.w(TAG,
                            "recording finalised with error code=${event.error} " +
                            "duration=${finalDurationMs}ms cause=${event.cause}")
                    } else {
                        Log.i(TAG, "recording finalised cleanly duration=${finalDurationMs}ms")
                    }
                    onFinalised(finalDurationMs)
                }
            }
    }

    fun stop() { active?.stop(); active = null }

    fun cancel() { active?.close(); active = null; provider?.unbindAll(); camera = null }

    fun unbind() { provider?.unbindAll(); provider = null; videoCapture = null; camera = null }

    private suspend fun awaitProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching { cont.resume(future.get()) }
                    .onFailure { cont.cancel(it) }
            }, ContextCompat.getMainExecutor(context))
        }

    companion object { private const val TAG = "VideoRecorder" }
}
