package rs.smobile.speak2act.feature.voicerecorder.domain

import java.io.File

interface AudioRecorder {
    fun start()
    /**
     * Stop recording and return the recorded file or null if none available.
     */
    fun stop(): File?

    /**
     * @return normalized amplitude in range [0f, 1f]
     */
    fun getAmplitude(): Float
}
