package uk.storitad.capture.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uk.storitad.capture.metadata.EntryMetadata
import uk.storitad.capture.metadata.MediaType
import uk.storitad.capture.metadata.MetadataRepository
import uk.storitad.capture.storage.FileManager
import kotlin.math.max

/** Pending list (unchanged UX): edit + delete + tap-to-open. */
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
    val repo = remember { MetadataRepository(FileManager.inboxDir(ctx)) }
    var refreshKey by remember { mutableStateOf(0) }
    val entries by produceState(initialValue = emptyList<EntryMetadata>(), refreshKey) {
        val all = repo.list()
        value = if (onlyPending) all.filter { !it.processed } else all
    }
    var pendingDelete by remember { mutableStateOf<EntryMetadata?>(null) }

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
                        else null,
                        onDelete = { pendingDelete = entry }
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    repo.delete(target)
                    pendingDelete = null
                    refreshKey++
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
            title = { Text("Delete entry?") },
            text = { Text("\"${target.subject}\" and its media will be removed.") }
        )
    }
}

@Composable
private fun EntryRow(
    entry: EntryMetadata,
    onOpen: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit
) {
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
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
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

/* ---------- History dashboard ---------- */

private data class MonthBucket(val year: Int, val month: Int, val voice: Int, val video: Int) {
    val total get() = voice + video
    val label get() = "%d-%02d".format(year, month)
}

private data class HistoryStats(
    val total: Int,
    val voice: Int,
    val video: Int,
    val totalSeconds: Long,
    val avgSeconds: Long,
    val longestSeconds: Long,
    val shortestSeconds: Long,
    val months: List<MonthBucket>,          // oldest → newest, exactly 12 trailing months anchored to today
    val recipients: List<Pair<String, Int>>, // sorted desc by count
    val tags: List<Pair<String, Int>>        // sorted desc by count, top 10
)

private fun computeStats(entries: List<EntryMetadata>): HistoryStats {
    if (entries.isEmpty()) return HistoryStats(0, 0, 0, 0, 0, 0, 0, emptyList(), emptyList(), emptyList())

    val voice = entries.count { it.mediaType == MediaType.VOICE }
    val video = entries.size - voice
    val durations = entries.map { it.durationSeconds }
    val total = durations.sum()
    val avg = total / entries.size
    val longest = durations.max()
    val shortest = durations.min()

    // Month bucketing in UTC — close enough for a personal timeline
    val byMonth = linkedMapOf<Pair<Int, Int>, IntArray>() // (y,m) → [voice,video]
    entries.forEach {
        val ldt = it.capturedAt.toLocalDateTime(TimeZone.UTC)
        val key = ldt.year to ldt.monthNumber
        val arr = byMonth.getOrPut(key) { intArrayOf(0, 0) }
        if (it.mediaType == MediaType.VOICE) arr[0]++ else arr[1]++
    }

    // Always render the trailing 12 months anchored to today so the axis is
    // stable — 1–2 months of data look like quiet stretches, not a single
    // full-width bar.
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    var y = now.year; var m = now.monthNumber
    repeat(11) { m--; if (m < 1) { m = 12; y-- } }
    val months = mutableListOf<MonthBucket>()
    repeat(12) {
        val arr = byMonth[y to m] ?: intArrayOf(0, 0)
        months += MonthBucket(y, m, arr[0], arr[1])
        m++; if (m > 12) { m = 1; y++ }
    }

    val recipientCounts = entries.flatMap { it.recipients }
        .groupingBy { it }.eachCount()
        .toList().sortedByDescending { it.second }

    val tagCounts = entries.flatMap { it.tags }
        .groupingBy { it }.eachCount()
        .toList().sortedByDescending { it.second }
        .take(10)

    return HistoryStats(
        total = entries.size, voice = voice, video = video,
        totalSeconds = total, avgSeconds = avg,
        longestSeconds = longest, shortestSeconds = shortest,
        months = months, recipients = recipientCounts, tags = tagCounts
    )
}

private fun fmtDuration(s: Long): String {
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return when {
        h > 0 -> "%dh %02dm".format(h, m)
        m > 0 -> "%dm %02ds".format(m, sec)
        else -> "${sec}s"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { MetadataRepository(FileManager.inboxDir(ctx)) }
    val stats by produceState(initialValue = computeStats(emptyList())) {
        value = computeStats(repo.list())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { pad ->
        if (stats.total == 0) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No captures yet")
            }
        } else {
            Column(
                Modifier.padding(pad).fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCards(stats)
                TimelineCard(stats)
                RecipientsCard(stats)
                TagsCard(stats)
            }
        }
    }
}

@Composable
private fun StatCards(s: HistoryStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Total", s.total.toString(), Modifier.weight(1f))
        StatTile("Voice", s.voice.toString(), Modifier.weight(1f))
        StatTile("Video", s.video.toString(), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        StatTile("Total time", fmtDuration(s.totalSeconds), Modifier.weight(1f))
        StatTile("Average", fmtDuration(s.avgSeconds), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        StatTile("Longest", fmtDuration(s.longestSeconds), Modifier.weight(1f))
        StatTile("Shortest", fmtDuration(s.shortestSeconds), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private val monthShortNames = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

@Composable
private fun TimelineCard(s: HistoryStats) {
    val peak = max(1, s.months.maxOf { it.total })
    val barMaxHeight = 120.dp
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Entries per month", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("voice + video, last 12 months", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(barMaxHeight + 16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                s.months.forEach { b ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (b.total > 0) b.total.toString() else "",
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (b.total > 0) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .height(barMaxHeight * (b.total.toFloat() / peak).coerceAtLeast(0.06f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            ) {
                                Column(Modifier.fillMaxSize()) {
                                    if (b.video > 0) {
                                        Box(
                                            Modifier.fillMaxWidth()
                                                .weight(b.video.toFloat())
                                                .background(MaterialTheme.colorScheme.secondary)
                                        )
                                    }
                                    if (b.voice > 0) {
                                        Box(
                                            Modifier.fillMaxWidth()
                                                .weight(b.voice.toFloat())
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Baseline tick so empty months still register as a column.
                            Box(
                                modifier = Modifier.fillMaxWidth(0.6f)
                                    .height(2.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                s.months.forEach { b ->
                    Text(
                        // Show year on January so the 12-month span reads clearly.
                        if (b.month == 1) "Jan\n${b.year % 100}" else monthShortNames[b.month - 1],
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendSwatch(MaterialTheme.colorScheme.primary, "Voice")
                LegendSwatch(MaterialTheme.colorScheme.secondary, "Video")
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RecipientsCard(s: HistoryStats) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Recipients", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            s.recipients.forEach { (name, count) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, modifier = Modifier.weight(1f))
                    Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun TagsCard(s: HistoryStats) {
    if (s.tags.isEmpty()) return
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Top tags", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            s.tags.forEach { (tag, count) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#$tag", modifier = Modifier.weight(1f))
                    Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
