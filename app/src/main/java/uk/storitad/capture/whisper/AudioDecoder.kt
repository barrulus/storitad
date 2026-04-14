package uk.storitad.capture.whisper

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * Decodes an audio track (m4a/mp4/aac) to mono, 16 kHz, 32-bit float PCM in
 * [-1, 1] — Whisper's expected input.
 *
 * Uses MediaExtractor + MediaCodec to produce 16-bit short samples, then
 * downmixes to mono and linearly resamples to 16 kHz. No extra deps.
 */
object AudioDecoder {

    private const val TARGET_SR = 16_000

    fun toMono16k(file: File): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            mime.startsWith("audio/")
        } ?: error("no audio track in ${file.name}")

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sourceSr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val pcm16 = ArrayList<Short>(sourceSr * 30)
        var endOfInput = false
        var endOfOutput = false
        val TIMEOUT_US = 10_000L

        while (!endOfOutput) {
            if (!endOfInput) {
                val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(inBuf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        endOfInput = true
                    } else {
                        val ts = extractor.sampleTime
                        codec.queueInputBuffer(inIdx, 0, size, ts, 0)
                        extractor.advance()
                    }
                }
            }

            val outIdx = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outIdx >= 0 -> {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    outBuf.position(bufferInfo.offset)
                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                    val shorts: ShortBuffer = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    while (shorts.hasRemaining()) pcm16.add(shorts.get())
                    codec.releaseOutputBuffer(outIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) endOfOutput = true
                }
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
            }
        }

        codec.stop(); codec.release(); extractor.release()

        // Downmix to mono if needed
        val mono: FloatArray = if (channels == 1) {
            FloatArray(pcm16.size) { pcm16[it] / 32768f }
        } else {
            val frames = pcm16.size / channels
            FloatArray(frames) { fi ->
                var sum = 0f
                for (c in 0 until channels) sum += pcm16[fi * channels + c] / 32768f
                sum / channels
            }
        }

        return if (sourceSr == TARGET_SR) mono else resample(mono, sourceSr, TARGET_SR)
    }

    /** Linear-interpolation resampler. Adequate for Whisper's input tolerance. */
    private fun resample(input: FloatArray, srcSr: Int, dstSr: Int): FloatArray {
        if (input.isEmpty()) return input
        val ratio = dstSr.toDouble() / srcSr
        val outLen = (input.size * ratio).toInt()
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i / ratio
            val i0 = srcPos.toInt()
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = (srcPos - i0).toFloat()
            out[i] = input[i0] * (1f - frac) + input[i1] * frac
        }
        return out
    }
}
