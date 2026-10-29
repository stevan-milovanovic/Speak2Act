package rs.smobile.speak2act.feature.voicerecorder.domain

/**
 * Turns a recorded spoken command into a [VoiceResult], on the device or in the cloud.
 */
interface SpeechToTransactionService {

    /**
     * Parses 16 kHz mono float [audio]. [onModelProgress] reports the first-use download of the
     * on-device model in `[0, 1]`.
     */
    suspend fun parse(audio: FloatArray, onModelProgress: (Float) -> Unit = {}): VoiceResult

    /**
     * Frees the loaded on-device model. Safe to call when nothing is loaded; the model is lazily
     * reloaded on the next [parse] call.
     */
    fun release()
}
