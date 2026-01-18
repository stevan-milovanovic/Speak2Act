package rs.smobile.speak2act.audio

import android.content.Context
import android.media.MediaRecorder
import java.io.File


class AudioRecorder(private val context: Context) {

    private companion object {
        private const val FILE_NAME: String = "instruction_recording.m4a"
        private const val ENCODING_BIT_RATE = 128_000 //quality vs size
        private const val SAMPLING_RATE = 44_100 //standard for speech
    }

    private var recorder: MediaRecorder? = null
    private lateinit var outputFile: File

    fun start(): File {
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

        return outputFile
    }

    fun stop() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
    }

    fun getAmplitude(): Int {
        return recorder?.maxAmplitude ?: 0
    }

}