package uk.storitad.capture.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uk.storitad.capture.capture.AudioPlayer
import uk.storitad.capture.metadata.EntryMetadata
import uk.storitad.capture.metadata.MetadataRepository
import uk.storitad.capture.storage.FileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(basename: String, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { MetadataRepository(FileManager.inboxDir(ctx)) }
    val entry by produceState<EntryMetadata?>(initialValue = null, basename) {
        value = runCatching { repo.read(basename) }.getOrNull()
    }
    val player = remember { AudioPlayer() }
    var playing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entry") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = { onEdit(basename) }) { Text("Edit") }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { pad ->
        val e = entry
        if (e == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(e.subject, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${e.durationSeconds}s · ${e.mediaType}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = {
                if (playing) { player.stop(); playing = false }
                else {
                    player.play(repo.mediaFile(e)) { playing = false }
                    playing = true
                }
            }) {
                Icon(
                    if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (playing) "Stop" else "Play")
            }
            HorizontalDivider()
            LabelValue("Recipients", e.recipients.joinToString(", "))
            e.mood?.let { LabelValue("Mood", it) }
            if (e.tags.isNotEmpty()) LabelValue("Tags", e.tags.joinToString(", "))
            e.notes?.let { LabelValue("Notes", it) }
            e.location?.let {
                val acc = it.accuracyMeters?.let { m -> " · ±${m.toInt()} m" }.orEmpty()
                LabelValue("Location", "%.4f, %.4f%s".format(it.latitude, it.longitude, acc))
            }
            LabelValue("Captured", e.capturedAt.toString())
            LabelValue("Device", e.device)
        }
    }

    if (confirmDelete) {
        val e = entry
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    e?.let { repo.delete(it); onBack() }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            title = { Text("Delete entry?") },
            text = { Text("Media and sidecar will be removed from the phone.") }
        )
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
