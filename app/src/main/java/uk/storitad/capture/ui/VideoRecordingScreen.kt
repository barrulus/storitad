package uk.storitad.capture.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.IBinder
import android.util.Size
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import uk.storitad.capture.capture.RecordingService
import uk.storitad.capture.capture.VideoRecorder
import uk.storitad.capture.settings.CaptureSettings
import uk.storitad.capture.storage.FileManager
import uk.storitad.capture.ui.drafts.DraftHolder
import uk.storitad.capture.ui.drafts.DraftLocationJob
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VideoRecordingScreen(onStopped: (String) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val perms = rememberMultiplePermissionsState(
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.POST_NOTIFICATIONS)
        }
    )

    LaunchedEffect(Unit) { if (!perms.allPermissionsGranted) perms.launchMultiplePermissionRequest() }

    if (!perms.permissions.take(2).all { it.status.isGranted }) {
        PermissionBlock(onCancel); return
    }

    val recorder = remember { VideoRecorder(ctx) }
    val view = LocalView.current
    val displayRotation = LocalConfiguration.current.let {
        // Re-read on configuration change. View.display is the right source of rotation.
        view.display?.rotation ?: Surface.ROTATION_0
    }
    LaunchedEffect(displayRotation) {
        recorder.displayRotation = displayRotation
    }
    var surface by remember { mutableStateOf<android.view.Surface?>(null) }
    var recording by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    var basename by remember { mutableStateOf<String?>(null) }
    var service by remember { mutableStateOf<RecordingService?>(null) }
    var useFront by remember { mutableStateOf(true) }
    var sensorOrientation by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    val conn = remember {
        object : ServiceConnection {
            override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
                service = (b as RecordingService.LocalBinder).service
            }
            override fun onServiceDisconnected(n: ComponentName?) { service = null }
        }
    }

    LaunchedEffect(useFront, surface) {
        val s = surface ?: return@LaunchedEffect
        recorder.useFrontCamera = useFront
        recorder.unbind()  // close prior camera handle (e.g., on flip)
        recorder.bind(s, Size(1280, 720))
        sensorOrientation = sensorOrientation
    }

    LaunchedEffect(recording, paused) {
        val start = System.currentTimeMillis() - elapsed
        while (recording && !paused) {
            elapsed = System.currentTimeMillis() - start
            service?.updateElapsed(elapsed)
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        val activity = ctx as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (previousOrientation != null) {
                activity?.requestedOrientation = previousOrientation
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (recording) recorder.cancel()
            recorder.release()
            runCatching { ctx.unbindService(conn) }
            ctx.stopService(Intent(ctx, RecordingService::class.java))
        }
    }

    SideEffect { view.keepScreenOn = recording }
    DisposableEffect(Unit) { onDispose { view.keepScreenOn = false } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                TextureView(c).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            st.setDefaultBufferSize(1280, 720)
                            applyPreviewTransform(this@apply, w, h, sensorOrientation, displayRotation, useFront)
                            surface = android.view.Surface(st)
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                            applyPreviewTransform(this@apply, w, h, sensorOrientation, displayRotation, useFront)
                        }
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            surface = null
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            update = { tv ->
                // Re-apply transform when camera flips (sensor orientation may change between front/back)
                applyPreviewTransform(tv, tv.width, tv.height, sensorOrientation, displayRotation, useFront)
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) recorder.pinchZoom(zoom)
                    }
                }
        )

        // Close — top-right
        IconButton(
            onClick = {
                if (recording) recorder.cancel()
                onCancel()
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        ) { Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White) }

        // Timer chip — bottom-left
        if (recording) {
            Surface(
                color = if (paused) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.error,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 72.dp)
            ) {
                Text(
                    if (paused) "PAUSED ${formatElapsed(elapsed)}" else formatElapsed(elapsed),
                    color = if (paused) MaterialTheme.colorScheme.onSurface else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Camera-swap — bottom-right (disabled while recording)
        IconButton(
            onClick = { if (!recording) useFront = !useFront },
            enabled = !recording,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 64.dp)
        ) {
            Icon(Icons.Filled.Cameraswitch, contentDescription = "Flip", tint = Color.White)
        }

        // Pause/Resume — appears only while recording, sits left of record button
        if (recording) {
            IconButton(
                onClick = {
                    if (paused) { recorder.resume(); paused = false }
                    else { recorder.pause(); paused = true }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 48.dp)
                    .size(64.dp)
            ) {
                Icon(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (paused) "Resume" else "Pause",
                    tint = Color.White
                )
            }
        }

        // Record / stop button
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                .size(80.dp)
                .clip(CircleShape)
                .background(if (recording) MaterialTheme.colorScheme.error else Color.White)
        ) {
            IconButton(
                onClick = {
                    if (!recording) {
                        val zone = TimeZone.currentSystemDefault()
                        val now = Clock.System.now()
                        val base = FileManager.basename(now, zone, "video")
                        val file = File(FileManager.inboxDir(ctx), FileManager.mediaFilename(base, "mp4"))
                        basename = base
                        DraftHolder.begin(base, now, zone, file)
                        if (CaptureSettings(ctx).autoAttachLocation) {
                            DraftLocationJob.start(ctx)
                        }

                        val i = Intent(ctx, RecordingService::class.java)
                            .putExtra(RecordingService.EXTRA_TYPE, RecordingService.TYPE_CAMERA)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
                        ctx.bindService(i, conn, Context.BIND_AUTO_CREATE)

                        scope.launch {
                            recorder.start(file) { durationMs ->
                                DraftHolder.finalise(durationMs)
                                recording = false
                                paused = false
                                runCatching { ctx.unbindService(conn) }
                                ctx.stopService(Intent(ctx, RecordingService::class.java))
                                onStopped(base)
                            }
                            recording = true
                            paused = false
                        }
                    } else {
                        recorder.stop()
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                    contentDescription = null,
                    tint = if (recording) Color.White else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionBlock(onCancel: () -> Unit) {
    Scaffold { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera and microphone permissions are required.")
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCancel) { Text("Back") }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val sec = ms / 1000
    return "%d:%02d".format(sec / 60, sec % 60)
}

/**
 * Build a TextureView transform that displays a 1280x720 sensor buffer correctly:
 *   1. undo the default buffer-to-view fill so we can think in buffer coords
 *   2. center the buffer at origin
 *   3. rotate by the angle that makes the image upright on screen
 *   4. cover-scale uniformly so the rotated buffer fills the view
 *   5. translate to view center
 *   6. mirror horizontally for the selfie preview
 *
 * Step 1 is the bit that earlier attempts missed; without it, post-rotation
 * just rotates an already-distorted (axis-stretched) fill.
 */
private fun applyPreviewTransform(
    view: TextureView,
    viewW: Int,
    viewH: Int,
    sensorOrientation: Int,
    displayRotation: Int,
    isFrontCamera: Boolean,
) {
    if (viewW == 0 || viewH == 0) return

    val bufW = 1280f
    val bufH = 720f
    val displayDegrees = when (displayRotation) {
        Surface.ROTATION_0 -> 0
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
    val rotation = if (isFrontCamera) {
        (sensorOrientation + displayDegrees) % 360
    } else {
        (sensorOrientation - displayDegrees + 360) % 360
    }
    val rotated = rotation == 90 || rotation == 270
    val rotW = if (rotated) bufH else bufW
    val rotH = if (rotated) bufW else bufH
    val scale = kotlin.math.max(viewW / rotW, viewH / rotH)

    val matrix = android.graphics.Matrix()
    // Step 1: undo default fill — view coord back to buffer coord.
    matrix.postScale(bufW / viewW, bufH / viewH)
    // Step 2: center buffer at origin.
    matrix.postTranslate(-bufW / 2f, -bufH / 2f)
    // Step 3: rotate around origin.
    matrix.postRotate(rotation.toFloat())
    // Step 4: cover-scale uniformly.
    matrix.postScale(scale, scale)
    // Step 5: translate to view center.
    matrix.postTranslate(viewW / 2f, viewH / 2f)
    // Step 6: selfie mirror.
    if (isFrontCamera) {
        matrix.postScale(-1f, 1f, viewW / 2f, viewH / 2f)
    }
    view.setTransform(matrix)
}
