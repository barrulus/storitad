// app/src/main/java/uk/storitad/capture/capture/VideoRecorder.kt
package uk.storitad.capture.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import java.util.concurrent.Executor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VideoRecorder(private val context: Context) {

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val cameraThread = HandlerThread("storitad-video-camera").also { it.start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var camera: CameraDevice? = null
    @Volatile private var session: CameraCaptureSession? = null
    private var recorder: MediaRecorder? = null
    private var previewSurface: Surface? = null
    private var recorderSurface: Surface? = null
    @Volatile private var captureRequestBuilder: CaptureRequest.Builder? = null

    private var startedAtMs: Long = 0
    private var pausedAtMs: Long = 0
    private var pausedAccumMs: Long = 0
    private var paused: Boolean = false
    private var recording: Boolean = false
    private var onFinalised: ((Long) -> Unit)? = null
    private var outputFile: File? = null

    var useFrontCamera: Boolean = true

    /** Surface.ROTATION_0/90/180/270. The screen sets this on bind and on configuration change. */
    @Volatile var displayRotation: Int = android.view.Surface.ROTATION_0

    /** Open camera and start preview into [previewSurface]. */
    suspend fun bind(previewSurface: Surface, previewSize: Size) {
        this.previewSurface = previewSurface
        val cameraId = pickCameraId(useFrontCamera)
        camera = openCamera(cameraId)
        val ch = cameraManager.getCameraCharacteristics(cameraId)
        val so = ch.get(CameraCharacteristics.SENSOR_ORIENTATION)
        val lf = ch.get(CameraCharacteristics.LENS_FACING)
        val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val texSizes = map?.getOutputSizes(android.graphics.SurfaceTexture::class.java)
        Log.d(TAG, "bind: cameraId=$cameraId useFrontCamera=$useFrontCamera lensFacing=$lf sensorOrientation=$so displayRotation=$displayRotation previewSize=$previewSize")
        Log.d(TAG, "bind: supported SurfaceTexture sizes=${texSizes?.joinToString { "${it.width}x${it.height}" }}")
        startPreviewSession()
    }

    /** Start recording into [target]. Calls [onFinalised] with the captured duration on stop/cancel. */
    @SuppressLint("MissingPermission")
    suspend fun start(target: File, onFinalised: (Long) -> Unit) {
        require(!recording) { "already recording" }
        outputFile = target
        this.onFinalised = onFinalised

        val rec = buildRecorder(target)
        recorder = rec
        recorderSurface = rec.surface

        rebuildSessionForRecording()
        rec.start()
        startedAtMs = System.currentTimeMillis()
        pausedAccumMs = 0
        paused = false
        recording = true
    }

    fun stop() {
        if (!recording) return
        finaliseRecording(durationMs = elapsedMs())
    }

    fun pause() {
        val r = recorder ?: return
        if (paused || !recording) return
        r.pause()
        pausedAtMs = System.currentTimeMillis()
        paused = true
    }

    fun resume() {
        val r = recorder ?: return
        if (!paused || !recording) return
        r.resume()
        pausedAccumMs += System.currentTimeMillis() - pausedAtMs
        paused = false
    }

    fun isPaused(): Boolean = paused

    fun cancel() {
        if (!recording) return
        finaliseRecording(durationMs = elapsedMs(), discard = true)
    }

    fun unbind() {
        runCatching { session?.close() }; session = null
        runCatching { camera?.close() }; camera = null
        runCatching { previewSurface = null }
    }

    /** Final teardown — call when the recorder is no longer needed. Thread is unusable after this. */
    fun release() {
        unbind()
        runCatching { cameraThread.quitSafely() }
    }

    /** Camera2 zoom — uses CONTROL_ZOOM_RATIO (API 30+). minSdk 34, so unconditionally available. */
    fun pinchZoom(factor: Float) {
        val cam = camera ?: return
        val builder = captureRequestBuilder ?: return
        val sess = session ?: return
        val ch = cameraManager.getCameraCharacteristics(cam.id)
        val range = ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) ?: return
        val current = builder.get(CaptureRequest.CONTROL_ZOOM_RATIO) ?: 1f
        val target = (current * factor).coerceIn(range.lower, range.upper)
        builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, target)
        sess.setRepeatingRequest(builder.build(), null, cameraHandler)
    }

    /** Sensor orientation for the currently-bound camera in degrees (0/90/180/270). 0 if not bound. */
    fun sensorOrientation(): Int {
        val cam = camera ?: return 0
        return cameraManager.getCameraCharacteristics(cam.id)
            .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    }

    fun elapsedMs(): Long {
        if (!recording) return 0
        val now = System.currentTimeMillis()
        return if (paused) now - startedAtMs - pausedAccumMs - (now - pausedAtMs)
               else now - startedAtMs - pausedAccumMs
    }

    // -------- private --------

    private fun pickCameraId(front: Boolean): String {
        val target = if (front) CameraCharacteristics.LENS_FACING_FRONT
                     else CameraCharacteristics.LENS_FACING_BACK
        return cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == target
        } ?: cameraManager.cameraIdList.first()
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(cameraId: String): CameraDevice =
        suspendCancellableCoroutine { cont ->
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) { cont.resume(device) }
                override fun onDisconnected(device: CameraDevice) {
                    Log.w(TAG, "camera $cameraId disconnected")
                    runCatching { device.close() }
                    if (cont.isActive) cont.resumeWithException(RuntimeException("camera disconnected"))
                }
                override fun onError(device: CameraDevice, error: Int) {
                    Log.w(TAG, "camera $cameraId error=$error")
                    runCatching { device.close() }
                    if (cont.isActive) cont.resumeWithException(RuntimeException("camera error $error"))
                }
            }, cameraHandler)
        }

    /** Executor that runs callbacks on the camera HandlerThread (matches the legacy createCaptureSession behaviour). */
    private val cameraExecutor = Executor { r -> cameraHandler.post(r) }

    /**
     * Configure a session with explicit output sizes. The preview [OutputConfiguration] uses the
     * `Size + SurfaceTexture::class.java` deferred-surface constructor: this pins the buffer size to
     * exactly [PREVIEW_SIZE] regardless of TextureView's view-driven SurfaceTexture sizing. The actual
     * Surface is attached after configuration via [OutputConfiguration.addSurface] +
     * [CameraCaptureSession.finalizeOutputConfigurations].
     */
    private suspend fun createPinnedSession(
        previewSurface: Surface,
        recorderSurface: Surface?,
    ): CameraCaptureSession = suspendCancellableCoroutine { cont ->
        val cam = camera ?: error("camera not opened")

        val previewConfig = OutputConfiguration(PREVIEW_SIZE, SurfaceTexture::class.java)
        val outputs = mutableListOf(previewConfig)
        if (recorderSurface != null) {
            outputs += OutputConfiguration(recorderSurface)
        }

        val sessionConfig = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            outputs,
            cameraExecutor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    // Attach the deferred preview Surface and finalize.
                    runCatching {
                        previewConfig.addSurface(previewSurface)
                        s.finalizeOutputConfigurations(listOf(previewConfig))
                    }.onFailure {
                        if (cont.isActive) cont.resumeWithException(it)
                        return
                    }
                    cont.resume(s)
                }
                override fun onConfigureFailed(s: CameraCaptureSession) {
                    if (cont.isActive) cont.resumeWithException(RuntimeException("session configure failed"))
                }
            },
        )
        cam.createCaptureSession(sessionConfig)
    }

    private suspend fun startPreviewSession() {
        val cam = camera ?: return
        val preview = previewSurface ?: return
        val builder = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(preview)
        }
        captureRequestBuilder = builder
        val s = createPinnedSession(preview, recorderSurface = null)
        session = s
        s.setRepeatingRequest(builder.build(), null, cameraHandler)
    }

    private suspend fun rebuildSessionForRecording() {
        val cam = camera ?: error("camera not opened")
        val preview = previewSurface ?: error("no preview surface")
        val recSurf = recorderSurface ?: error("no recorder surface")
        runCatching { session?.close() }
        val builder = cam.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(preview)
            addTarget(recSurf)
        }
        captureRequestBuilder = builder
        val s = createPinnedSession(preview, recorderSurface = recSurf)
        session = s
        s.setRepeatingRequest(builder.build(), null, cameraHandler)
    }

    private fun buildRecorder(target: File): MediaRecorder {
        val unprocessedSupported =
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = pickAudioSource(unprocessedSupported)

        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
                else @Suppress("DEPRECATION") MediaRecorder()

        // Note: the preview SurfaceTexture (via OutputConfiguration(Size, SurfaceTexture::class))
        // is auto-rotated upright by the HAL, but a plain OutputConfiguration(Surface) — what
        // MediaRecorder uses — receives sensor-orientation frames. So we need the standard hint
        // here to make players rotate the recorded buffer on playback.
        val sensorRotation = camera?.let {
            cameraManager.getCameraCharacteristics(it.id)
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        } ?: 0
        val displayDegrees = when (displayRotation) {
            android.view.Surface.ROTATION_0 -> 0
            android.view.Surface.ROTATION_90 -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }
        val hint = if (useFrontCamera) {
            (sensorRotation + displayDegrees) % 360
        } else {
            (sensorRotation - displayDegrees + 360) % 360
        }
        r.setOrientationHint(hint)

        r.setAudioSource(source)
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setVideoSize(1280, 720)
        r.setVideoFrameRate(30)
        r.setVideoEncodingBitRate(6_000_000)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(44100)
        r.setAudioEncodingBitRate(128_000)
        r.setOutputFile(target.absolutePath)
        r.prepare()
        return r
    }

    private fun finaliseRecording(durationMs: Long, discard: Boolean = false) {
        val r = recorder
        recording = false
        try {
            if (r != null) {
                if (paused) runCatching { r.resume() }
                runCatching { r.stop() }
                r.release()
            }
        } finally {
            recorder = null
            recorderSurface = null
            paused = false
            val cb = onFinalised
            onFinalised = null
            if (discard) outputFile?.delete()
            outputFile = null
            cb?.invoke(durationMs)
        }
        // After recording ends we close the session; preview can be re-bound by the caller if needed.
        runCatching { session?.close() }; session = null
    }

    companion object {
        private const val TAG = "VideoRecorder"
        /** Pinned preview/recorder buffer size — must match MediaRecorder.setVideoSize and a supported camera output. */
        private val PREVIEW_SIZE = Size(1280, 720)
    }
}
