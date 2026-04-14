package uk.storitad.capture.metadata

import kotlinx.serialization.json.Json
import java.io.File

class MetadataRepository(private val inboxDir: File) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    init {
        inboxDir.mkdirs()
    }

    fun write(entry: EntryMetadata) {
        val basename = entry.mediaFile.substringBeforeLast('.')
        val file = File(inboxDir, "$basename.json")
        file.writeText(json.encodeToString(EntryMetadata.serializer(), entry))
    }

    fun read(basename: String): EntryMetadata {
        val file = File(inboxDir, "$basename.json")
        return json.decodeFromString(EntryMetadata.serializer(), file.readText())
    }

    fun list(): List<EntryMetadata> =
        inboxDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull {
                runCatching {
                    json.decodeFromString(EntryMetadata.serializer(), it.readText())
                }.getOrNull()
            }
            ?.sortedByDescending { it.capturedAt }
            ?: emptyList()

    fun listPending(): List<EntryMetadata> = list().filter { !it.processed }

    fun mediaFile(entry: EntryMetadata): File = File(inboxDir, entry.mediaFile)

    fun delete(entry: EntryMetadata) {
        val basename = entry.mediaFile.substringBeforeLast('.')
        File(inboxDir, "$basename.json").delete()
        File(inboxDir, entry.mediaFile).delete()
    }
}
