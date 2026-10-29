package rs.smobile.speak2act.feature.voicerecorder.domain

/**
 * Executes a [Transaction] after the user has confirmed it.
 */
interface TransactionExecutor {
    suspend fun execute(transaction: Transaction): Result<Unit>
}
