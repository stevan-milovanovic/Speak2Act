package rs.smobile.speak2act.feature.voicerecorder.domain

/**
 * Abstraction for Speech to Transaction AI operations used by the feature.
 */
interface SpeechToTransactionAiService {
    suspend fun extractTransaction(audioBytes: ByteArray): Result<Transaction?>
}