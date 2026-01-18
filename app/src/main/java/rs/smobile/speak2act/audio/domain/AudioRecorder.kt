package rs.smobile.speak2act.audio.domain

import java.io.File

interface AudioRecorder {
    fun start()
    fun stop(): File

    /**
     * @return normalized amplitude in range [0f, 1f]
     */
    fun getAmplitude(): Float
}