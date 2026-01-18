package rs.smobile.speak2act.audio.data

import android.content.Context
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import rs.smobile.speak2act.audio.domain.AudioRecorder
import java.io.File
import javax.inject.Inject

class AndroidAudioRecorder @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AudioRecorder {

    private companion object {
        private const val FILE_NAME: String = "instruction_recording.m4a"
        private const val ENCODING_BIT_RATE = 128_000 //quality vs size
        private const val SAMPLING_RATE = 44_100 //standard for speech

        /**
         * Maximum amplitude value for 16-bit PCM audio
         * Most Android microphone recordings are PCM 16-bit
         * A signed 16-bit integer ranges from -32768 to 32767
         */
        private const val MAX_AMPLITUDE = 32768f
    }

    private var recorder: MediaRecorder? = null
    private lateinit var outputFile: File

    override fun start() {
        outputFile = File(context.filesDir, FILE_NAME)

        recorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(ENCODING_BIT_RATE)
            setAudioSamplingRate(SAMPLING_RATE)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
    }

    override fun stop(): File {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        return outputFile
    }

    override fun getAmplitude(): Float {
        val amplitude = recorder?.maxAmplitude?.coerceAtLeast(0)?.toFloat() ?: 0f
        return amplitude / MAX_AMPLITUDE
    }

}