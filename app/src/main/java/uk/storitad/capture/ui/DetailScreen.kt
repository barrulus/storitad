package uk.storitad.capture.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import uk.storitad.capture.capture.AudioPlayer
import uk.storitad.capture.metadata.EntryMetadata
import uk.storitad.capture.metadata.MediaType
import uk.storitad.capture.metadata.MetadataRepository
import uk.storitad.capture.storage.FileManager
import uk.storitad.capture.whisper.DownloadState
import uk.storitad.capture.whisper.ModelManager
import uk.storitad.capture.whisper.TINY_EN
import uk.storitad.capture.whisper.TranscribeWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(basename: String, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { MetadataRepository(FileManager.inboxDir(ctx)) }
    val modelManager = remember { ModelManager(ctx) }

    var reloadKey by remember { mutableStateOf(0) }
    val entry by produceState<EntryMetadata?>(initialValue = null, basename, reloadKey) {
        value = runCatching { repo.read(basename) }.getOrNull()
    }
    val player = remember { AudioPlayer() }
    var playing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    val workInfos by WorkManager.getInstance(ctx)
        .getWorkInfosForUniqueWorkLiveData(TranscribeWorker.uniqueName(basename))
        .observeAsStateList()
    val runningInfo = workInfos.firstOrNull { !it.state.isFinished }
    val transcribing = runningInfo != null

    LaunchedEffect(workInfos) {
        val latest = workInfos.lastOrNull()
        if (latest?.state == WorkInfo.State.SUCCEEDED) reloadKey++
    }

    val dl by modelManager.state.collectAsStateWithLifecycle()

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

            if (e.mediaType == MediaType.VIDEO) {
                VideoPlayer(
                    file = repo.mediaFile(e),
                    modifier = Modifier.fillMaxWidth().height(280.dp)
                )
            } else {
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
            }

            HorizontalDivider()

            // Transcript panel
            TranscriptPanel(
                entry = e,
                transcribing = transcribing,
                hasModel = modelManager.isPresent(TINY_EN),
                onTranscribe = {
                    if (modelManager.isPresent(TINY_EN)) {
                        TranscribeWorker.enqueue(ctx, basename)
                    } else {
                        showModelSheet = true
                    }
                },
                onSaveEdits = { edited ->
                    repo.write(e.copy(transcriptDraft = edited))
                    reloadKey++
                }
            )

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

    val pickModel = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                modelManager.importFromStream(TINY_EN) {
                    ctx.contentResolver.openInputStream(uri)!!
                }
            }.onSuccess {
                showModelSheet = false
                TranscribeWorker.enqueue(ctx, basename)
            }
        }
    }

    if (showModelSheet) {
        ModelDownloadDialog(
            state = dl,
            onDismiss = { showModelSheet = false },
            onConfirm = {
                scope.launch {
                    runCatching { modelManager.ensure(TINY_EN) }
                        .onSuccess {
                            showModelSheet = false
                            TranscribeWorker.enqueue(ctx, basename)
                        }
                }
            },
            onSideload = { pickModel.launch(arrayOf("*/*")) }
        )
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
private fun TranscriptPanel(
    entry: EntryMetadata,
    transcribing: Boolean,
    hasModel: Boolean,
    onTranscribe: () -> Unit,
    onSaveEdits: (String) -> Unit
) {
    var edited by remember(entry.transcriptDraft) { mutableStateOf(entry.transcriptDraft ?: "") }
    val dirty = edited != (entry.transcriptDraft ?: "")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Transcript", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (entry.transcriptModel != null) {
                Text(
                    entry.transcriptModel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when {
            transcribing -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Transcribing…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            entry.transcriptDraft == null -> {
                Button(onClick = onTranscribe) {
                    Text(if (hasModel) "Transcribe" else "Transcribe (needs one-time download)")
                }
            }
            else -> {
                OutlinedTextField(
                    value = edited,
                    onValueChange = { edited = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = dirty, onClick = { onSaveEdits(edited) }) { Text("Save edits") }
                    TextButton(onClick = onTranscribe) { Text("Re-transcribe") }
                }
            }
        }
    }
}

@Composable
private fun ModelDownloadDialog(
    state: DownloadState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onSideload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TINY_EN.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("One-time download into app storage. No other network is used by Storitad.")
                Text(
                    "If network is blocked, tap \"Sideload\" and pick a pre-downloaded ggml-tiny.en.bin (SHA-256 is verified).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when (val s = state) {
                    is DownloadState.Downloading -> {
                        val frac = if (s.total > 0) s.bytes.toFloat() / s.total else 0f
                        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                        Text("${s.bytes / 1024 / 1024} / ${s.total / 1024 / 1024} MB")
                    }
                    DownloadState.Verifying -> Text("Verifying SHA-256…")
                    is DownloadState.Failed -> Text("Failed: ${s.message}",
                        color = MaterialTheme.colorScheme.error)
                    DownloadState.Ready -> Text("Ready.")
                    DownloadState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            val enabled = state !is DownloadState.Downloading && state !is DownloadState.Verifying
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = enabled, onClick = onSideload) { Text("Sideload") }
                TextButton(enabled = enabled, onClick = onConfirm) { Text("Download") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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

@Composable
private fun androidx.lifecycle.LiveData<List<WorkInfo>>.observeAsStateList(): State<List<WorkInfo>> {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val state = remember { mutableStateOf<List<WorkInfo>>(emptyList()) }
    DisposableEffect(this, owner) {
        val obs = androidx.lifecycle.Observer<List<WorkInfo>> { state.value = it ?: emptyList() }
        observe(owner, obs)
        onDispose { removeObserver(obs) }
    }
    return state
}
