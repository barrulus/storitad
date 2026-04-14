package uk.storitad.capture.whisper

internal object WhisperNative {
    init { System.loadLibrary("storitad-whisper") }

    @JvmStatic external fun nativeLoadModel(modelPath: String): Long
    @JvmStatic external fun nativeTranscribe(ctx: Long, pcm: FloatArray, sampleRate: Int): String
    @JvmStatic external fun nativeRelease(ctx: Long)
}
