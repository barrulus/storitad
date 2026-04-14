package uk.storitad.capture.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uk.storitad.capture.metadata.MetadataRepository
import uk.storitad.capture.storage.FileManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRecordVoice: () -> Unit,
    onRecordVideo: () -> Unit,
    onPending: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val pendingCount by produceState(initialValue = 0) {
        value = runCatching {
            MetadataRepository(FileManager.inboxDir(ctx)).listPending().size
        }.getOrDefault(0)
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Storitad") },
            actions = {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Record a message for later.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            ActionCard(
                title = "Record Voice",
                subtitle = "Tap to start",
                icon = Icons.Filled.Mic,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onRecordVoice
            )
            ActionCard(
                title = "Record Video",
                subtitle = "Tap to start",
                icon = Icons.Filled.Videocam,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onRecordVideo
            )
            ActionCard(
                title = "On device",
                subtitle = if (pendingCount == 0) "Nothing pending"
                else "$pendingCount pending · tap to review and edit",
                icon = Icons.Filled.Inbox,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                badge = if (pendingCount > 0) pendingCount.toString() else null,
                onClick = onPending
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onHistory,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("View all history") }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    outlined: Boolean = false,
    enabled: Boolean = true,
    badge: String? = null,
    onClick: () -> Unit
) {
    val mod = Modifier
        .fillMaxWidth()
        .alpha(if (enabled) 1f else 0.6f)
        .clickable(enabled = enabled, onClick = onClick)
    val body: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(48.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = contentColor)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = contentColor)
            if (badge != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
    if (outlined) OutlinedCard(modifier = mod) { body() }
    else Card(
        modifier = mod,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) { body() }
}
