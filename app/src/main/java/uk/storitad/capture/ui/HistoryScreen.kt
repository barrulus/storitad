package uk.storitad.capture.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uk.storitad.capture.metadata.EntryMetadata
import uk.storitad.capture.metadata.MediaType
import uk.storitad.capture.metadata.MetadataRepository
import uk.storitad.capture.storage.FileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryListScreen(
    title: String,
    onlyPending: Boolean,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }
    val entries by produceState(initialValue = emptyList<EntryMetadata>(), refreshKey) {
        val all = MetadataRepository(FileManager.inboxDir(ctx)).list()
        value = if (onlyPending) all.filter { !it.processed } else all
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { pad ->
        if (entries.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (onlyPending) "Nothing pending" else "No captures yet")
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    EntryRow(
                        entry = entry,
                        onOpen = { onOpen(entry.mediaFile.substringBeforeLast('.')) },
                        onEdit = if (onlyPending)
                            { { onEdit(entry.mediaFile.substringBeforeLast('.')) } }
                        else null
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: EntryMetadata, onOpen: () -> Unit, onEdit: (() -> Unit)? = null) {
    val zone = runCatching { TimeZone.of(entry.timezone) }
        .getOrDefault(TimeZone.currentSystemDefault())
    val ldt = entry.capturedAt.toLocalDateTime(zone)
    val icon = if (entry.mediaType == MediaType.VOICE) "🎙️" else "🎥"
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$icon ${entry.subject}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (onEdit != null) {
                    TextButton(onClick = onEdit) { Text("Edit") }
                }
            }
            Text(
                "%04d-%02d-%02d %02d:%02d · %dm %02ds".format(
                    ldt.year, ldt.monthNumber, ldt.dayOfMonth,
                    ldt.hour, ldt.minute,
                    entry.durationSeconds / 60, entry.durationSeconds % 60
                ),
                style = MaterialTheme.typography.bodySmall
            )
            val footer = buildList {
                entry.recipients.firstOrNull()?.let { add(it) }
                entry.mood?.let { add(it) }
                if (entry.location != null) add("📍")
            }.joinToString(" · ")
            if (footer.isNotEmpty()) Text(footer, style = MaterialTheme.typography.bodySmall)
        }
    }
}
