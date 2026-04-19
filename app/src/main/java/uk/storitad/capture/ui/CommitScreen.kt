package uk.storitad.capture.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import uk.storitad.capture.BuildConfig
import uk.storitad.capture.capture.AudioPlayer
import uk.storitad.capture.metadata.EntryMetadata
import uk.storitad.capture.metadata.MediaType
import uk.storitad.capture.metadata.MetadataRepository
import uk.storitad.capture.metadata.RecentTagsStore
import uk.storitad.capture.metadata.Recipient
import uk.storitad.capture.metadata.RecipientsRepository
import uk.storitad.capture.metadata.SubjectBuilder
import uk.storitad.capture.storage.FileManager
import uk.storitad.capture.ui.drafts.DraftHolder
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CommitScreen(
    basename: String,
    onSaved: () -> Unit,
    onRerecord: () -> Unit,
    onDiscard: () -> Unit,
) {
    val ctx = LocalContext.current
    val repo = remember { MetadataRepository(FileManager.inboxDir(ctx)) }
    val recipientsRepo = remember { RecipientsRepository(ctx) }
    val tagsStore = remember { RecentTagsStore(ctx, repo) }

    val recipientPresets: List<Recipient> = remember {
        runCatching { recipientsRepo.list() }.getOrDefault(emptyList())
    }
    val recipientLabels: Map<String, String> = remember(recipientPresets) {
        recipientPresets.associate { it.id to it.label }
    }

    val draft = DraftHolder.get()
    val isVideo = draft?.mediaFile?.extension?.equals("mp4", ignoreCase = true) == true

    val player = remember { AudioPlayer() }
    var playing by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val recipients = remember { mutableStateListOf<String>().apply { add("family") } }
    val userTags = remember { mutableStateListOf<String>() }
    val recentTags = remember { tagsStore.topN(6) }
    val selectedRecent = remember { mutableStateListOf<String>() }
    var tagInput by remember { mutableStateOf("") }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    fun commitTag(raw: String) {
        val t = raw.trim().trimEnd(',').trim()
        if (t.isEmpty()) return
        val existing = (userTags + selectedRecent).any { it.equals(t, ignoreCase = true) }
        if (existing) return
        userTags.add(t)
    }

    val subjectPreview = remember(recipients.toList(), userTags.toList(), selectedRecent.toList(), draft?.capturedAt, draft?.timezone) {
        val d = draft ?: return@remember ""
        SubjectBuilder.build(
            capturedAt = d.capturedAt,
            timezone = d.timezone,
            recipients = recipients.toList(),
            recipientLabels = recipientLabels,
            tags = (userTags + selectedRecent).distinctBy { it.lowercase() },
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = { confirmDiscard = true }) {
                    Icon(Icons.Filled.Close, contentDescription = "Discard")
                }
            },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isVideo && draft != null) {
                VideoPlayer(
                    file = draft.mediaFile,
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val f = draft?.mediaFile ?: return@OutlinedButton
                            if (playing) { player.stop(); playing = false }
                            else { player.play(f) { playing = false }; playing = true }
                        },
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (playing) "Stop" else "Play")
                    }
                    Text("${(draft?.durationMs ?: 0) / 1000}s")
                }
            }

            OutlinedButton(
                onClick = { player.stop(); DraftHolder.clear(); onRerecord() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Re-record") }

            Text("Recipients", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recipientPresets.forEach { r ->
                    FilterChip(
                        selected = recipients.contains(r.id),
                        onClick = {
                            if (recipients.contains(r.id)) recipients.remove(r.id)
                            else recipients.add(r.id)
                        },
                        label = { Text("${r.emoji} ${r.label}") },
                    )
                }
            }

            Text("Tags", style = MaterialTheme.typography.labelLarge)
            if (userTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    userTags.toList().forEach { t ->
                        InputChip(
                            selected = true,
                            onClick = { userTags.remove(t) },
                            label = { Text(t) },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, contentDescription = "Remove tag")
                            },
                        )
                    }
                }
            }
            if (recentTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recentTags.forEach { t ->
                        FilterChip(
                            selected = selectedRecent.contains(t),
                            onClick = {
                                if (selectedRecent.contains(t)) selectedRecent.remove(t)
                                else selectedRecent.add(t)
                            },
                            label = { Text(t) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = tagInput,
                onValueChange = { value ->
                    if (value.endsWith(",")) {
                        commitTag(value)
                        tagInput = ""
                    } else {
                        tagInput = value
                    }
                },
                label = { Text("+ tag") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { commitTag(tagInput); tagInput = "" },
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                subjectPreview,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    // Commit any half-typed tag first.
                    if (tagInput.isNotBlank()) {
                        commitTag(tagInput); tagInput = ""
                    }
                    val d = draft ?: return@Button
                    val tagsFinal = (userTags + selectedRecent).distinctBy { it.lowercase() }
                    val subject = SubjectBuilder.build(
                        capturedAt = d.capturedAt,
                        timezone = d.timezone,
                        recipients = recipients.toList(),
                        recipientLabels = recipientLabels,
                        tags = tagsFinal,
                    )
                    val mediaType = if (isVideo) MediaType.VIDEO else MediaType.VOICE
                    val entry = EntryMetadata(
                        id = UUID.randomUUID().toString(),
                        capturedAt = d.capturedAt,
                        durationSeconds = d.durationMs / 1000,
                        timezone = d.timezone.id,
                        mediaFile = d.mediaFile.name,
                        mediaType = mediaType,
                        mimeType = if (isVideo) "video/mp4" else "audio/mp4",
                        subject = subject,
                        recipients = recipients.toList(),
                        tags = tagsFinal,
                        location = d.locationFix,
                        device = Build.MODEL,
                        appVersion = BuildConfig.VERSION_NAME,
                    )
                    repo.write(entry)
                    tagsStore.bump(tagsFinal)
                    DraftHolder.consume()
                    onSaved()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save") }

            Spacer(Modifier.height(8.dp))
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
            text = { Text("This deletes the recording and cannot be undone.") },
        )
    }
}
