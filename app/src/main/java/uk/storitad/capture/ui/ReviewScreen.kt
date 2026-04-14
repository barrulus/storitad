package uk.storitad.capture.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.storitad.capture.capture.AudioPlayer
import uk.storitad.capture.ui.drafts.DraftHolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    basename: String,
    onContinue: () -> Unit,
    onRerecord: () -> Unit,
    onDiscard: () -> Unit
) {
    val draft = DraftHolder.get()
    val isVideo = draft?.mediaFile?.extension?.equals("mp4", ignoreCase = true) == true
    val player = remember { AudioPlayer() }
    var playing by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Review") },
            navigationIcon = {
                TextButton(onClick = { confirmDiscard = true }) { Text("Discard") }
            }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(basename, style = MaterialTheme.typography.titleMedium)
            Text("${(draft?.durationMs ?: 0) / 1000}s")

            if (isVideo && draft != null) {
                VideoPlayer(
                    file = draft.mediaFile,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).height(320.dp)
                )
            } else {
                Button(onClick = {
                    val f = draft?.mediaFile ?: return@Button
                    if (playing) { player.stop(); playing = false }
                    else { player.play(f) { playing = false }; playing = true }
                }) {
                    Icon(
                        if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (playing) "Stop" else "Play")
                }
            }

            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { DraftHolder.clear(); onRerecord() },
                    modifier = Modifier.weight(1f)
                ) { Text("Re-record") }
                Button(
                    onClick = { player.stop(); onContinue() },
                    modifier = Modifier.weight(1f)
                ) { Text("Continue") }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    player.stop()
                    DraftHolder.clear()
                    onDiscard()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep") } },
            title = { Text("Discard recording?") },
            text = { Text("This deletes the audio and cannot be undone.") }
        )
    }
}
