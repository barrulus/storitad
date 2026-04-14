package uk.storitad.capture.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import uk.storitad.capture.capture.AudioRecorder
import uk.storitad.capture.capture.RecordingService
import uk.storitad.capture.storage.FileManager
import uk.storitad.capture.ui.drafts.DraftHolder
import java.io.File

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(onStopped: (String) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val micPerm = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val notifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) else null

    LaunchedEffect(Unit) {
        if (!micPerm.status.isGranted) micPerm.launchPermissionRequest()
        if (notifPerm != null && !notifPerm.status.isGranted) notifPerm.launchPermissionRequest()
    }

    if (!micPerm.status.isGranted) { PermissionNeeded(onCancel); return }

    var recorder by remember { mutableStateOf<AudioRecorder?>(null) }
    var basename by remember { mutableStateOf<String?>(null) }
    var elapsed by remember { mutableStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var service by remember { mutableStateOf<RecordingService?>(null) }
    val amps by (recorder?.amplitudes?.collectAsState() ?: remember { mutableStateOf(FloatArray(0)) })

    val conn = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, b: IBinder?) {
                service = (b as RecordingService.LocalBinder).service
            }
            override fun onServiceDisconnected(name: ComponentName?) { service = null }
        }
    }

    LaunchedEffect(Unit) {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val base = FileManager.basename(now, zone, "voice")
        val inbox = FileManager.inboxDir(ctx)
        val mediaFile = File(inbox, FileManager.mediaFilename(base, "m4a"))
        val r = AudioRecorder(ctx)
        r.start(mediaFile)
        recorder = r
        basename = base
        DraftHolder.begin(base, now, zone, mediaFile)

        val i = Intent(ctx, RecordingService::class.java)
            .putExtra(RecordingService.EXTRA_TYPE, RecordingService.TYPE_MIC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        ctx.bindService(i, conn, Context.BIND_AUTO_CREATE)
    }

    LaunchedEffect(recorder, paused) {
        while (recorder != null) {
            val r = recorder ?: break
            elapsed = r.elapsedMs()
            service?.updateElapsed(elapsed)
            delay(200)
        }
    }

    fun teardown() {
        recorder?.cancel(); recorder = null
        runCatching { ctx.unbindService(conn) }
        ctx.stopService(Intent(ctx, RecordingService::class.java))
    }

    DisposableEffect(Unit) {
        onDispose {
            if (recorder != null) teardown()
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Recording") }, navigationIcon = {
            TextButton(onClick = {
                teardown()
                DraftHolder.clear()
                onCancel()
            }) { Text("Cancel") }
        })
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = if (paused) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.errorContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
            ) {
                Text(
                    if (paused) "PAUSED" else "RECORDING",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (paused) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Text(formatElapsed(elapsed), style = MaterialTheme.typography.displayLarge)

            Waveform(amps = amps, Modifier.fillMaxWidth().height(72.dp))

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val r = recorder ?: return@OutlinedButton
                        if (paused) { r.resume(); paused = false }
                        else { r.pause(); paused = true }
                    },
                    modifier = Modifier.weight(1f).height(64.dp)
                ) {
                    Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (paused) "Resume" else "Pause")
                }
                Button(
                    onClick = {
                        val r = recorder ?: return@Button
                        val durationMs = r.stop()
                        recorder = null
                        runCatching { ctx.unbindService(conn) }
                        ctx.stopService(Intent(ctx, RecordingService::class.java))
                        val b = basename ?: return@Button
                        DraftHolder.finalise(durationMs)
                        onStopped(b)
                    },
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun Waveform(amps: FloatArray, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (amps.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val barW = ((size.width - gap * (amps.size - 1)) / amps.size).coerceAtLeast(1f)
        val cy = size.height / 2f
        amps.forEachIndexed { i, a ->
            val h = (a * size.height).coerceAtLeast(2.dp.toPx())
            val x = i * (barW + gap)
            drawRect(
                color = barColor,
                topLeft = Offset(x, cy - h / 2f),
                size = Size(barW, h)
            )
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

@Composable
private fun PermissionNeeded(onCancel: () -> Unit) {
    Scaffold { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Microphone permission is required.")
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCancel) { Text("Back") }
        }
    }
}
