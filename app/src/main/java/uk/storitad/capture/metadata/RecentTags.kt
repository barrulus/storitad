package uk.storitad.capture.metadata

import kotlinx.datetime.Instant

object RecentTags {
    fun bump(existing: List<String>, newTags: List<String>, cap: Int = 30): List<String> {
        val result = mutableListOf<String>()
        val seenLower = mutableSetOf<String>()
        for (raw in newTags) {
            val t = raw.trim()
            if (t.isEmpty()) continue
            val lower = t.lowercase()
            if (lower in seenLower) continue
            result.add(t); seenLower.add(lower)
        }
        for (t in existing) {
            val lower = t.lowercase()
            if (lower in seenLower) continue
            result.add(t); seenLower.add(lower)
        }
        return result.take(cap)
    }

    fun seedFromEntries(entries: List<Pair<Instant, List<String>>>, cap: Int = 30): List<String> {
        // Iterate oldest → newest so each bump pushes the most recent tags to the head.
        val sorted = entries.sortedBy { it.first }
        var acc = emptyList<String>()
        for ((_, tags) in sorted) {
            acc = bump(acc, tags, cap)
        }
        return acc
    }
}
