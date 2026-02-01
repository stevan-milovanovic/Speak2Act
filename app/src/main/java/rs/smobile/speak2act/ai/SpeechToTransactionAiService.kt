package rs.smobile.speak2act.ai

import rs.smobile.speak2act.domain.Transaction

/**
 * Abstraction for Speech to Transaction AI operations used by the app.
 * Implementations should translate audio bytes into domain objects.
 */
interface SpeechToTransactionAiService {
    suspend fun extractTransaction(audioBytes: ByteArray): Result<Transaction?>
}
