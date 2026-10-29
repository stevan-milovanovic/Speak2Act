package rs.smobile.speak2act.feature.voicerecorder.domain

/**
 * Cloud fallback that turns a spoken command into a [VoiceResult] with Gemini.
 */
interface CloudTransactionService {
    /** Sends only the on-device [transcript] (text) to the cloud. */
    suspend fun fromTranscript(transcript: String): VoiceResult

    /** Sends the recorded audio as a WAV file to the cloud. Used only by the `CLOUD_AUDIO` route. */
    suspend fun fromAudio(wav: ByteArray): VoiceResult
}
