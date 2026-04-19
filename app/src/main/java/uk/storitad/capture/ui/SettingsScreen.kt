package uk.storitad.capture.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import uk.storitad.capture.metadata.Recipient
import uk.storitad.capture.metadata.RecipientsRepository
import uk.storitad.capture.reminders.ReminderPrefs
import uk.storitad.capture.reminders.ReminderScheduler
import uk.storitad.capture.settings.CaptureSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { RecipientsRepository(ctx) }
    val reminders = remember { ReminderPrefs(ctx) }

    val capture = remember { CaptureSettings(ctx) }
    var autoLocation by remember { mutableStateOf(capture.autoAttachLocation) }
    var locPermDenied by remember { mutableStateOf(false) }

    val locPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            capture.autoAttachLocation = true
            autoLocation = true
            locPermDenied = false
        } else {
            locPermDenied = true
        }
    }

    fun onToggleLocation(newValue: Boolean) {
        if (newValue) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                capture.autoAttachLocation = true
                autoLocation = true
                locPermDenied = false
            } else {
                locPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            capture.autoAttachLocation = false
            autoLocation = false
        }
    }

    val items = remember { mutableStateListOf<Recipient>() }
    var showAdd by remember { mutableStateOf(false) }
    var bootstrapped by remember { mutableStateOf(false) }

    var remindersEnabled by remember { mutableStateOf(reminders.enabled) }
    var eveningMinutes by remember { mutableStateOf(reminders.eveningMinutes) }
    var morningMinutes by remember { mutableStateOf(reminders.morningMinutes) }
    var permDenied by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            reminders.enabled = true
            remindersEnabled = true
            permDenied = false
            ReminderScheduler.reschedule(ctx)
        } else {
            permDenied = true
        }
    }

    fun onToggle(newValue: Boolean) {
        if (newValue) {
            val needsPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
            if (needsPrompt) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                reminders.enabled = true
                remindersEnabled = true
                permDenied = false
                ReminderScheduler.reschedule(ctx)
            }
        } else {
            reminders.enabled = false
            remindersEnabled = false
            ReminderScheduler.cancelAll(ctx)
        }
    }

    LaunchedEffect(Unit) {
        if (!bootstrapped) {
            items.clear(); items.addAll(repo.list()); bootstrapped = true
        }
    }

    fun persistRecipients() = repo.save(items.toList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add recipient") }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                RemindersCard(
                    enabled = remindersEnabled,
                    eveningMinutes = eveningMinutes,
                    morningMinutes = morningMinutes,
                    permDenied = permDenied,
                    onToggle = ::onToggle,
                    onEveningChange = {
                        eveningMinutes = it
                        reminders.eveningMinutes = it
                        if (remindersEnabled) ReminderScheduler.reschedule(ctx)
                    },
                    onMorningChange = {
                        morningMinutes = it
                        reminders.morningMinutes = it
                        if (remindersEnabled) ReminderScheduler.reschedule(ctx)
                    }
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Auto-attach location while recording",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "When on, a GPS fix is fetched in the background at record start and saved with the entry.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = autoLocation, onCheckedChange = ::onToggleLocation)
                        }
                        if (locPermDenied) {
                            Text(
                                "Fine location permission is required.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "Recipients",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }
            items(items, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(r.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                r.id,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            items.remove(r)
                            persistRecipients()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddRecipientDialog(
            existingIds = items.map { it.id }.toSet(),
            onDismiss = { showAdd = false },
            onAdd = { r ->
                items.add(r)
                persistRecipients()
                showAdd = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindersCard(
    enabled: Boolean,
    eveningMinutes: Int,
    morningMinutes: Int,
    permDenied: Boolean,
    onToggle: (Boolean) -> Unit,
    onEveningChange: (Int) -> Unit,
    onMorningChange: (Int) -> Unit,
) {
    var showEveningPicker by remember { mutableStateOf(false) }
    var showMorningPicker by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily recording reminder",
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Evening nudge, morning follow-up if nothing recorded in 24 h.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (permDenied) {
                Text(
                    "Notifications permission is required for reminders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            TimeRow(
                label = "Evening nudge",
                minutes = eveningMinutes,
                enabled = enabled,
                onClick = { showEveningPicker = true }
            )
            TimeRow(
                label = "Morning follow-up",
                minutes = morningMinutes,
                enabled = enabled,
                onClick = { showMorningPicker = true }
            )
        }
    }

    if (showEveningPicker) {
        TimePickerDialog(
            initialMinutes = eveningMinutes,
            onDismiss = { showEveningPicker = false },
            onConfirm = { onEveningChange(it); showEveningPicker = false }
        )
    }
    if (showMorningPicker) {
        TimePickerDialog(
            initialMinutes = morningMinutes,
            onDismiss = { showMorningPicker = false },
            onConfirm = { onMorningChange(it); showMorningPicker = false }
        )
    }
}

@Composable
private fun TimeRow(label: String, minutes: Int, enabled: Boolean, onClick: () -> Unit) {
    val hh = minutes / 60
    val mm = minutes % 60
    val pretty = "%02d:%02d".format(hh, mm)
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text(pretty, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecipientDialog(
    existingIds: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (Recipient) -> Unit
) {
    var id by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    val idValid = id.matches(Regex("^[a-z][a-z0-9_-]{0,23}$")) && id !in existingIds
    val canAdd = idValid && label.isNotBlank() && emoji.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New recipient") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = emoji, onValueChange = { emoji = it },
                    label = { Text("Emoji") }, singleLine = true
                )
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Label") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )
                OutlinedTextField(
                    value = id, onValueChange = { id = it.lowercase() },
                    label = { Text("id (lowercase, unique)") },
                    singleLine = true,
                    isError = id.isNotBlank() && !idValid,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Ascii
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canAdd,
                onClick = { onAdd(Recipient(id.trim(), label.trim(), emoji.trim())) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
