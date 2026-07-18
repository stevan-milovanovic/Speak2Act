package rs.smobile.speak2act.feature.voicerecorder.domain

interface AudioRecorder {
    fun start()

    /**
     * Stop recording and return the captured audio as 16 kHz mono normalized floats
     * (empty if nothing was recorded).
     */
    fun stop(): FloatArray

    /**
     * @return normalized amplitude in range [0f, 1f]
     */
    fun getAmplitude(): Float
}
