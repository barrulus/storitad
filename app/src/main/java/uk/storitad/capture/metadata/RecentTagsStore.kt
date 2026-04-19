package uk.storitad.capture.metadata

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class RecentTagsStore(ctx: Context, private val repo: MetadataRepository) {
    private val prefs = ctx.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(String.serializer())

    fun topN(n: Int = 6): List<String> {
        ensureSeeded()
        return load().take(n)
    }

    fun bump(tags: List<String>) {
        ensureSeeded()
        save(RecentTags.bump(load(), tags, CAP))
    }

    private fun load(): List<String> =
        prefs.getString(KEY_LIST, null)
            ?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    private fun save(list: List<String>) {
        prefs.edit().putString(KEY_LIST, json.encodeToString(listSerializer, list)).apply()
    }

    private fun ensureSeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        val entries = runCatching {
            repo.list().map { it.capturedAt to it.tags }
        }.getOrDefault(emptyList())
        save(RecentTags.seedFromEntries(entries, CAP))
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    companion object {
        private const val FILE_NAME = "recent_tags"
        private const val KEY_LIST = "list"
        private const val KEY_SEEDED = "seeded"
        private const val CAP = 30
    }
}
