package uk.storitad.capture.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class Transcriber(private val modelPath: String) {

    /**
     * Transcribes an m4a/mp4 file. Loads the model fresh each call (cheaper
     * than holding ~75 MB indefinitely for a feature used occasionally).
     */
    suspend fun transcribe(mediaFile: File): String = withContext(Dispatchers.Default) {
        require(File(modelPath).exists()) { "model missing at $modelPath" }
        val pcm = AudioDecoder.toMono16k(mediaFile)
        val ctx = WhisperNative.nativeLoadModel(modelPath)
        require(ctx != 0L) { "failed to load whisper model" }
        try {
            WhisperNative.nativeTranscribe(ctx, pcm, 16_000).trim()
        } finally {
            WhisperNative.nativeRelease(ctx)
        }
    }
}
