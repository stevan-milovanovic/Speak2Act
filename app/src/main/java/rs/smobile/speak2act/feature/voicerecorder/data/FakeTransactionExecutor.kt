package rs.smobile.speak2act.feature.voicerecorder.data

import kotlinx.coroutines.delay
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction
import rs.smobile.speak2act.feature.voicerecorder.domain.TransactionExecutor
import javax.inject.Inject

/**
 * Demo [TransactionExecutor] that simulates a payment backend.
 */
class FakeTransactionExecutor @Inject constructor() : TransactionExecutor {

    private companion object {
        private const val SIMULATED_LATENCY_MS = 800L
    }

    // Demo only: simulates a payment backend; no real money is moved.
    override suspend fun execute(transaction: Transaction): Result<Unit> {
        delay(SIMULATED_LATENCY_MS)
        return Result.success(Unit)
    }
}
