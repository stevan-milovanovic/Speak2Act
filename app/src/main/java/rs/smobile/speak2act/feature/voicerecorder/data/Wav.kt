package rs.smobile.speak2act.feature.voicerecorder.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val WAV_HEADER_BYTES = 44
private const val BITS_PER_SAMPLE = 16
private const val CHANNELS = 1
private const val PCM16_MAX = 32_767f

/**
 * Encodes normalized mono samples ([-1, 1], clamped) as a PCM16 little-endian WAV file with a
 * 44-byte RIFF/WAVE header. The output is `44 + 2 · size` bytes.
 */
fun FloatArray.toWav(sampleRate: Int = 16_000): ByteArray {
    val dataBytes = size * BITS_PER_SAMPLE / 8
    val byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8
    val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8
    val buffer = ByteBuffer.allocate(WAV_HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
    buffer.putInt(36 + dataBytes)
    buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
    buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
    buffer.putInt(16)                          // fmt chunk size
    buffer.putShort(1)                         // PCM
    buffer.putShort(CHANNELS.toShort())
    buffer.putInt(sampleRate)
    buffer.putInt(byteRate)
    buffer.putShort(blockAlign.toShort())
    buffer.putShort(BITS_PER_SAMPLE.toShort())
    buffer.put("data".toByteArray(Charsets.US_ASCII))
    buffer.putInt(dataBytes)
    for (sample in this) {
        buffer.putShort((sample.coerceIn(-1f, 1f) * PCM16_MAX).toInt().toShort())
    }
    return buffer.array()
}
