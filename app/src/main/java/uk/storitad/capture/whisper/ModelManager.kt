package uk.storitad.capture.whisper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ModelSpec(
    val id: String,
    val filename: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long
) {
    val label: String get() = "Whisper $id (${sizeBytes / 1024 / 1024} MB)"
}

val TINY_EN = ModelSpec(
    id = "tiny.en",
    filename = "ggml-tiny.en.bin",
    url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
    sha256 = "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f",
    sizeBytes = 77_704_715L
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val bytes: Long, val total: Long) : DownloadState
    data object Verifying : DownloadState
    data object Ready : DownloadState
    data class Failed(val message: String) : DownloadState
}

class ModelManager(private val ctx: Context) {

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private fun modelsDir(): File = File(ctx.filesDir, "models").apply { mkdirs() }
    fun fileFor(spec: ModelSpec): File = File(modelsDir(), spec.filename)
    fun isPresent(spec: ModelSpec): Boolean = fileFor(spec).exists()

    suspend fun ensure(spec: ModelSpec): File = withContext(Dispatchers.IO) {
        val target = fileFor(spec)
        if (target.exists()) {
            _state.value = DownloadState.Ready
            return@withContext target
        }
        download(spec, target)
        target
    }

    /** Sideload a .bin the user pre-downloaded outside the app. */
    suspend fun importFromStream(
        spec: ModelSpec,
        open: () -> java.io.InputStream
    ): File = withContext(Dispatchers.IO) {
        val target = fileFor(spec)
        val tmp = File(target.parentFile, "${target.name}.part")
        runCatching { tmp.delete() }
        _state.value = DownloadState.Downloading(0, spec.sizeBytes)
        try {
            open().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        _state.value = DownloadState.Downloading(done, spec.sizeBytes)
                    }
                }
            }
            _state.value = DownloadState.Verifying
            val actual = sha256(tmp)
            if (!actual.equals(spec.sha256, ignoreCase = true)) {
                tmp.delete()
                _state.value = DownloadState.Failed("SHA-256 mismatch ($actual)")
                error("SHA-256 mismatch")
            }
            tmp.renameTo(target)
            _state.value = DownloadState.Ready
        } catch (t: Throwable) {
            runCatching { tmp.delete() }
            _state.value = DownloadState.Failed(t.message ?: "import failed")
            throw t
        }
        target
    }

    private suspend fun download(spec: ModelSpec, target: File) = withContext(Dispatchers.IO) {
        val tmp = File(target.parentFile, "${target.name}.part")
        runCatching { tmp.delete() }

        val conn = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: spec.sizeBytes
            _state.value = DownloadState.Downloading(0, total)
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        _state.value = DownloadState.Downloading(done, total)
                    }
                }
            }
        } catch (t: Throwable) {
            runCatching { tmp.delete() }
            _state.value = DownloadState.Failed(t.message ?: "network error")
            throw t
        } finally {
            conn.disconnect()
        }

        _state.value = DownloadState.Verifying
        val actual = sha256(tmp)
        if (!actual.equals(spec.sha256, ignoreCase = true)) {
            tmp.delete()
            _state.value = DownloadState.Failed("SHA-256 mismatch ($actual)")
            error("SHA-256 mismatch")
        }
        tmp.renameTo(target)
        _state.value = DownloadState.Ready
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (s.read(buf).also { read = it } != -1) md.update(buf, 0, read)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
