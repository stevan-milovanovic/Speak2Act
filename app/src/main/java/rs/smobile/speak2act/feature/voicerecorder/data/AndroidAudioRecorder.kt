package rs.smobile.speak2act.feature.voicerecorder.data

import android.content.Context
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import rs.smobile.speak2act.feature.voicerecorder.domain.AudioRecorder
import java.io.File
import javax.inject.Inject

class AndroidAudioRecorder @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AudioRecorder {

    private companion object {
        private const val FILE_NAME: String = "instruction_recording.m4a"
        private const val ENCODING_BIT_RATE = 128_000
        private const val SAMPLING_RATE = 44_100
        private const val MAX_AMPLITUDE = 32768f
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording: Boolean = false

    override fun start() {
        outputFile = File(context.filesDir, FILE_NAME)

        try {
            recorder = MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(ENCODING_BIT_RATE)
                setAudioSamplingRate(SAMPLING_RATE)
                setOutputFile(outputFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
        } catch (t: Throwable) {
            recorder?.release()
            recorder = null
            isRecording = false
            try {
                outputFile?.takeIf { it.exists() }?.delete()
            } catch (_: Throwable) {
            }
            outputFile = null
            throw t
        }
    }

    override fun stop(): File? {
        if (!isRecording && recorder == null) return outputFile?.takeIf { it.exists() }

        try {
            recorder?.apply {
                try {
                    stop()
                } catch (_: Throwable) {
                } finally {
                    try {
                        release()
                    } catch (_: Throwable) {
                    }
                }
            }
        } finally {
            recorder = null
            isRecording = false
        }

        return outputFile?.takeIf { it.exists() }
    }

    override fun getAmplitude(): Float {
        return try {
            val amplitude = recorder?.maxAmplitude?.coerceAtLeast(0)?.toFloat() ?: 0f
            (amplitude / MAX_AMPLITUDE).coerceIn(0f, 1f)
        } catch (_: Throwable) {
            0f
        }
    }

}
