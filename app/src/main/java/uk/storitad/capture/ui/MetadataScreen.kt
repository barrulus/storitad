package uk.storitad.capture.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uk.storitad.capture.BuildConfig
import uk.storitad.capture.location.LocationProvider
import uk.storitad.capture.metadata.EntryMetadata
import uk.storitad.capture.metadata.GeoFix
import uk.storitad.capture.metadata.MediaType
import uk.storitad.capture.metadata.MetadataRepository
import uk.storitad.capture.storage.FileManager
import uk.storitad.capture.ui.drafts.DraftHolder
import java.util.UUID

@Serializable
private data class RecipientPreset(val id: String, val label: String, val emoji: String)

@Serializable
private data class RecipientsConfig(val recipients: List<RecipientPreset>)

private val MOODS = listOf(
    "happy" to "😊", "proud" to "💪", "reflective" to "🤔", "grateful" to "🙏",
    "excited" to "🎉", "calm" to "😌", "nostalgic" to "💭", "loving" to "❤️",
    "bittersweet" to "🥹", "hopeful" to "🌅"
)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MetadataScreen(basename: String, editExisting: Boolean, onSaved: () -> Unit, onDiscard: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { MetadataRepository(FileManager.inboxDir(ctx)) }
    val draft = DraftHolder.get()

    val recipientPresets = remember {
        runCatching {
            val text = ctx.assets.open("recipients.json").bufferedReader().use { it.readText() }
            Json { ignoreUnknownKeys = true }
                .decodeFromString(RecipientsConfig.serializer(), text).recipients
        }.getOrDefault(emptyList())
    }

    val existing: EntryMetadata? = remember(basename, editExisting) {
        if (editExisting) runCatching { repo.read(basename) }.getOrNull() else null
    }

    var subject by remember { mutableStateOf(existing?.subject ?: "") }
    val recipients = remember {
        mutableStateListOf<String>().apply {
            addAll(existing?.recipients ?: listOf("family"))
        }
    }
    var mood by remember { mutableStateOf(existing?.mood) }
    var tagsText by remember { mutableStateOf(existing?.tags?.joinToString(", ") ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var confirmDiscard by remember { mutableStateOf(false) }

    var locationOn by remember { mutableStateOf(existing?.location != null) }
    var locationFix by remember { mutableStateOf<GeoFix?>(existing?.location) }
    var locationStatus by remember { mutableStateOf(existing?.location?.accuracyMeters?.let { "±${it.toInt()} m" } ?: "") }
    val locProvider = remember { LocationProvider(ctx) }
    val locPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    fun fetchLocation() {
        locationStatus = "Fetching…"
        scope.launch {
            when (val outcome = locProvider.currentFix()) {
                is LocationProvider.Outcome.Ok -> {
                    locationFix = outcome.fix
                    locationStatus = outcome.fix.accuracyMeters
                        ?.let { "±${it.toInt()} m" } ?: "Fix acquired"
                }
                LocationProvider.Outcome.NoPermission -> {
                    locationFix = null; locationStatus = "Permission denied"
                }
                LocationProvider.Outcome.LocationOff -> {
                    locationFix = null; locationStatus = "Location services off"
                }
                LocationProvider.Outcome.NoProvider -> {
                    locationFix = null; locationStatus = "No provider available"
                }
                LocationProvider.Outcome.Timeout -> {
                    locationFix = null; locationStatus = "Timed out — try outdoors"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editExisting) "Edit details" else "Details") },
                navigationIcon = { TextButton(onClick = { confirmDiscard = true }) { Text(if (editExisting) "Back" else "Discard") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = subject, onValueChange = { subject = it },
                label = { Text("Subject *") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Recipients", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recipientPresets.forEach { r ->
                    FilterChip(
                        selected = recipients.contains(r.id),
                        onClick = {
                            if (recipients.contains(r.id)) recipients.remove(r.id) else recipients.add(r.id)
                        },
                        label = { Text("${r.emoji} ${r.label}") }
                    )
                }
            }

            Text("Mood", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MOODS.forEach { (id, emoji) ->
                    FilterChip(
                        selected = mood == id,
                        onClick = { mood = if (mood == id) null else id },
                        label = { Text("$emoji $id") }
                    )
                }
            }

            // Location toggle
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Attach GPS", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            locationStatus.ifEmpty { "Off" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = locationOn,
                        onCheckedChange = { checked ->
                            locationOn = checked
                            if (checked) {
                                if (locPermission.status.isGranted) fetchLocation()
                                else locPermission.launchPermissionRequest()
                            } else {
                                locationFix = null
                                locationStatus = ""
                            }
                        }
                    )
                }
            }
            LaunchedEffect(locPermission.status.isGranted, locationOn) {
                if (locationOn && locPermission.status.isGranted && locationFix == null) fetchLocation()
            }

            OutlinedTextField(
                value = tagsText, onValueChange = { tagsText = it },
                label = { Text("Tags (comma-separated)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
            )

            Button(
                onClick = {
                    val base = existing ?: run {
                        val d = draft ?: return@Button
                        if (subject.isBlank()) return@Button
                        EntryMetadata(
                            id = UUID.randomUUID().toString(),
                            capturedAt = d.capturedAt,
                            durationSeconds = d.durationMs / 1000,
                            timezone = d.timezone.id,
                            mediaFile = d.mediaFile.name,
                            mediaType = MediaType.VOICE,
                            mimeType = "audio/mp4",
                            subject = subject.trim(),
                            device = Build.MODEL,
                            appVersion = BuildConfig.VERSION_NAME
                        )
                    }
                    val entry = base.copy(
                        subject = subject.trim(),
                        recipients = recipients.toList(),
                        mood = mood,
                        tags = tagsText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        notes = notes.ifBlank { null },
                        location = if (locationOn) locationFix else null
                    )
                    repo.write(entry)
                    if (!editExisting) DraftHolder.consume()
                    onSaved()
                },
                enabled = subject.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Save") }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    if (!editExisting) DraftHolder.clear()
                    onDiscard()
                }) { Text(if (editExisting) "Leave" else "Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep") } },
            title = { Text(if (editExisting) "Leave without saving?" else "Discard recording?") },
            text = {
                Text(
                    if (editExisting) "Unsaved edits will be lost."
                    else "This deletes the audio and cannot be undone."
                )
            }
        )
    }
}
