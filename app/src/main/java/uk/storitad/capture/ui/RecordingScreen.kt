package uk.storitad.capture.ui

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import uk.storitad.capture.storage.FileManager
import uk.storitad.capture.ui.drafts.DraftHolder
import java.io.File

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(onStopped: (String) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val permission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(Unit) {
        if (!permission.status.isGranted) permission.launchPermissionRequest()
    }

    if (!permission.status.isGranted) {
        PermissionNeeded(onCancel)
        return
    }

    var recorder by remember { mutableStateOf<AudioRecorder?>(null) }
    var basename by remember { mutableStateOf<String?>(null) }
    var startAt by remember { mutableStateOf(0L) }
    var elapsed by remember { mutableStateOf(0L) }

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
        startAt = System.currentTimeMillis()
        DraftHolder.begin(base, now, zone, mediaFile)
    }

    LaunchedEffect(startAt) {
        while (recorder != null) {
            elapsed = System.currentTimeMillis() - startAt
            delay(200)
        }
    }

    DisposableEffect(Unit) { onDispose { recorder?.cancel() } }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Recording") }, navigationIcon = {
            TextButton(onClick = {
                recorder?.cancel(); recorder = null
                DraftHolder.clear()
                onCancel()
            }) { Text("Cancel") }
        })
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(formatElapsed(elapsed), style = MaterialTheme.typography.displayLarge)
            Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = Color.Red)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val r = recorder ?: return@Button
                    val durationMs = r.stop()
                    recorder = null
                    val b = basename ?: return@Button
                    DraftHolder.finalise(durationMs)
                    onStopped(b)
                },
                modifier = Modifier.fillMaxWidth().height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Stop")
            }
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
