package uk.storitad.capture.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import uk.storitad.capture.capture.RecordingService
import uk.storitad.capture.capture.VideoRecorder
import uk.storitad.capture.storage.FileManager
import uk.storitad.capture.ui.drafts.DraftHolder
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VideoRecordingScreen(onStopped: (String) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
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
    val preview = remember { Preview.Builder().build() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    var basename by remember { mutableStateOf<String?>(null) }
    var service by remember { mutableStateOf<RecordingService?>(null) }
    var useFront by remember { mutableStateOf(true) }

    val conn = remember {
        object : ServiceConnection {
            override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
                service = (b as RecordingService.LocalBinder).service
            }
            override fun onServiceDisconnected(n: ComponentName?) { service = null }
        }
    }

    LaunchedEffect(useFront, previewView) {
        val v = previewView ?: return@LaunchedEffect
        recorder.useFrontCamera = useFront
        preview.setSurfaceProvider(v.surfaceProvider)
        recorder.bind(owner, preview)
    }

    LaunchedEffect(recording) {
        val start = System.currentTimeMillis()
        while (recording) {
            elapsed = System.currentTimeMillis() - start
            service?.updateElapsed(elapsed)
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (recording) recorder.cancel()
            recorder.unbind()
            runCatching { ctx.unbindService(conn) }
            ctx.stopService(Intent(ctx, RecordingService::class.java))
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PreviewView(c).also {
                    previewView = it
                    preview.setSurfaceProvider(it.surfaceProvider)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (recording) recorder.cancel()
                onCancel()
            }) { Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White) }

            Spacer(Modifier.weight(1f))
            if (recording) {
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = CircleShape
                ) {
                    Text(
                        formatElapsed(elapsed),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { if (!recording) useFront = !useFront }) {
                Icon(Icons.Filled.Cameraswitch, contentDescription = "Flip", tint = Color.White)
            }
        }

        // Record button
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

                        val i = Intent(ctx, RecordingService::class.java)
                            .putExtra(RecordingService.EXTRA_TYPE, RecordingService.TYPE_CAMERA)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
                        ctx.bindService(i, conn, Context.BIND_AUTO_CREATE)

                        recorder.start(file) { durationMs ->
                            DraftHolder.finalise(durationMs)
                            recording = false
                            runCatching { ctx.unbindService(conn) }
                            ctx.stopService(Intent(ctx, RecordingService::class.java))
                            onStopped(base)
                        }
                        recording = true
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
