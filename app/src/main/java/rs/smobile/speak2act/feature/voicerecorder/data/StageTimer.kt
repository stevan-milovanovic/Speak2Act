package rs.smobile.speak2act.feature.voicerecorder.data

import rs.smobile.speak2act.feature.voicerecorder.domain.StageTimings
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Measures the stages of one voice command with a monotonic clock.
 */
internal class StageTimer(private val timeSource: TimeSource = TimeSource.Monotonic) {

    private val start = timeSource.markNow()
    private var transcribe: Duration? = null
    private var grammar: Duration? = null
    private var llm: Duration? = null
    private var cloud: Duration? = null

    suspend fun <T> transcribe(block: suspend () -> T): T = measure(block) { transcribe = it }
    suspend fun <T> grammar(block: suspend () -> T): T = measure(block) { grammar = it }
    suspend fun <T> llm(block: suspend () -> T): T = measure(block) { llm = it }
    suspend fun <T> cloud(block: suspend () -> T): T = measure(block) { cloud = it }

    fun finish(): StageTimings = StageTimings(
        transcribe = transcribe,
        grammar = grammar,
        cloud = cloud,
        total = start.elapsedNow(),
        llm = llm,
    )

    private suspend fun <T> measure(block: suspend () -> T, record: (Duration) -> Unit): T {
        val mark = timeSource.markNow()
        try {
            return block()
        } finally {
            record(mark.elapsedNow())
        }
    }
}
