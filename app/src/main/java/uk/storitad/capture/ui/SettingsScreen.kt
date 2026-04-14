package uk.storitad.capture.ui

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
import uk.storitad.capture.metadata.Recipient
import uk.storitad.capture.metadata.RecipientsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { RecipientsRepository(ctx) }
    val items = remember { mutableStateListOf<Recipient>() }
    var showAdd by remember { mutableStateOf(false) }
    var bootstrapped by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!bootstrapped) {
            items.clear(); items.addAll(repo.list()); bootstrapped = true
        }
    }

    fun persist() = repo.save(items.toList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recipients") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add") }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                            persist()
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
                persist()
                showAdd = false
            }
        )
    }
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
