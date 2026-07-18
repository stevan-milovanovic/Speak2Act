package rs.smobile.speak2act.feature.voicerecorder.domain

/**
 * Transcribes recorded audio on-device and parses it into a [ParsedVoiceTransaction].
 */
interface SpeechToTransactionService {

    /**
     * Transcribes 16 kHz mono float [audio] with the local Whisper model (downloading it on first
     * use, reporting progress via [onModelDownloadProgress]) and parses the transcript.
     *
     * @return the parsed transaction, or `null` when the speech is not a recognisable command.
     */
    suspend fun parse(
        audio: FloatArray,
        onModelDownloadProgress: (Float) -> Unit = {},
    ): ParsedVoiceTransaction?

    /**
     * Frees the loaded Whisper model / native context to release memory when it is no longer
     * needed (e.g. when the user leaves the recorder screen). Safe to call when nothing is loaded;
     * the model is lazily reloaded on the next [parse] call.
     */
    fun release()
}
