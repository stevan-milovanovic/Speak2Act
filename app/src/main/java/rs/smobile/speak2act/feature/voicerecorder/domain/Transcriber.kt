package rs.smobile.speak2act.feature.voicerecorder.domain

/**
 * Speech-to-text step of the voice pipeline.
 */
interface Transcriber {
    /**
     * Transcribes 16 kHz mono float [audio]. [onModelProgress] reports a first-use model download.
     */
    suspend fun transcribe(audio: FloatArray, onModelProgress: (Float) -> Unit = {}): String

    /** Frees the loaded model; it is reloaded lazily on the next [transcribe]. */
    fun release()
}
