package rs.smobile.speak2act.feature.voicerecorder.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import rs.smobile.speak2act.feature.voicerecorder.domain.AudioRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Captures microphone audio as 16 kHz mono PCM and exposes it as the normalized float
 * array that whisper.cpp expects, while tracking a live amplitude level for the waveform UI.
 *
 * The RECORD_AUDIO permission must be granted by the caller before [start] is invoked.
 */
class WhisperAudioRecorder @Inject constructor() : AudioRecorder {

    private val isRecording = AtomicBoolean(false)
    private val pcmBuffer = ByteArrayOutputStream()

    @Volatile
    private var recordingThread: Thread? = null

    @Volatile
    private var currentAmplitude: Float = 0f

    @SuppressLint("MissingPermission")
    override fun start() {
        if (isRecording.getAndSet(true)) return

        synchronized(pcmBuffer) { pcmBuffer.reset() }
        currentAmplitude = 0f

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, SAMPLE_RATE_HZ * BYTES_PER_SAMPLE)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize,
        )
        audioRecord.startRecording()

        recordingThread = Thread({
            // Read in small frames (not the whole 1 s buffer) so the amplitude used by the
            // waveform refreshes frequently instead of once per second.
            val chunk = ByteArray(READ_FRAME_SAMPLES * BYTES_PER_SAMPLE)
            while (isRecording.get()) {
                val read = audioRecord.read(chunk, 0, chunk.size)
                if (read > 0) {
                    synchronized(pcmBuffer) { pcmBuffer.write(chunk, 0, read) }
                    currentAmplitude = amplitudeOf(chunk, read)
                }
            }
            audioRecord.stop()
            audioRecord.release()
        }, "whisper-audio-record").apply { start() }
    }

    /**
     * Stops recording and returns the captured audio as a normalized float array.
     * Returns an empty array if nothing was recorded.
     */
    override fun stop(): FloatArray {
        if (!isRecording.getAndSet(false)) return FloatArray(0)
        recordingThread?.join()
        recordingThread = null
        currentAmplitude = 0f
        val pcm = synchronized(pcmBuffer) { pcmBuffer.toByteArray() }
        return pcm16ToFloat(pcm)
    }

    override fun getAmplitude(): Float = currentAmplitude

    /**
     * Live level of a PCM16 chunk, normalized to `[0f, 1f]` for the waveform UI. Uses the peak
     * (loudest) sample rather than RMS and applies a perceptual boost so ordinary speech produces a
     * clearly visible, responsive waveform.
     */
    private fun amplitudeOf(pcm: ByteArray, length: Int): Float {
        val shortBuffer = ByteBuffer.wrap(pcm, 0, length).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        if (!shortBuffer.hasRemaining()) return 0f
        var peak = 0
        while (shortBuffer.hasRemaining()) {
            val sample = abs(shortBuffer.get().toInt())
            if (sample > peak) peak = sample
        }
        val normalized = peak / PCM16_MAX
        return (sqrt(normalized) * AMPLITUDE_GAIN).coerceIn(0f, 1f)
    }

    private fun pcm16ToFloat(pcm: ByteArray): FloatArray {
        val shortBuffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val floats = FloatArray(shortBuffer.remaining())
        var index = 0
        while (shortBuffer.hasRemaining()) {
            floats[index++] = shortBuffer.get() / PCM16_MAX
        }
        return floats
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2
        private const val PCM16_MAX = 32_768.0f

        // ~50 ms frames so the waveform amplitude updates ~20x/second.
        private const val READ_FRAME_SAMPLES = SAMPLE_RATE_HZ / 20

        // Perceptual boost applied to the peak level so ordinary speech fills the waveform.
        private const val AMPLITUDE_GAIN = 1.6f
    }
}
