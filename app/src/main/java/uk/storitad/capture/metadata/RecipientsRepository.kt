package uk.storitad.capture.metadata

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Recipient(val id: String, val label: String, val emoji: String)

@Serializable
data class RecipientsConfig(val recipients: List<Recipient>)

class RecipientsRepository(private val ctx: Context) {
    private val file: File get() = File(ctx.filesDir, "recipients.json")
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun list(): List<Recipient> {
        ensureSeeded()
        return json.decodeFromString(RecipientsConfig.serializer(), file.readText()).recipients
    }

    fun save(items: List<Recipient>) {
        file.writeText(
            json.encodeToString(RecipientsConfig.serializer(), RecipientsConfig(items))
        )
    }

    private fun ensureSeeded() {
        if (file.exists()) return
        val seed = ctx.assets.open("recipients.json").bufferedReader().use { it.readText() }
        file.writeText(seed)
    }
}
