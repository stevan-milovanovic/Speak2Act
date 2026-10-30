package rs.smobile.speak2act.feature.voicerecorder.domain

import kotlin.time.Duration

/**
 * Where a result was produced: [EDGE] = the local grammar, [EDGE_LLM] = the on-device language model,
 * [CLOUD_TEXT] = Gemini on the transcript, [CLOUD_AUDIO] = Gemini on the recording.
 */
enum class InferenceRoute { EDGE, EDGE_LLM, CLOUD_TEXT, CLOUD_AUDIO }

/** Time spent in each stage; null = the stage didn't run. */
data class StageTimings(
    val transcribe: Duration? = null,
    val grammar: Duration? = null,
    val cloud: Duration? = null,
    val total: Duration = Duration.ZERO,
    val llm: Duration? = null,
)

/** Outcome of one spoken command, whichever route produced it. */
sealed interface VoiceResult {
    val timings: StageTimings

    /** A transaction the user still has to confirm. */
    data class Proposed(
        val transaction: Transaction,
        val source: InferenceRoute,
        val transcript: String? = null,
        override val timings: StageTimings = StageTimings(),
    ) : VoiceResult

    /** The model needs more information and asks a clarifying question. */
    data class AskBack(
        val question: String,
        val source: InferenceRoute,
        override val timings: StageTimings = StageTimings(),
    ) : VoiceResult

    /** Nothing usable was understood; nothing is sent. */
    data class NotUnderstood(
        val transcript: String,
        override val timings: StageTimings = StageTimings(),
    ) : VoiceResult
}

/** Returns a copy of this result with [timings] replaced. */
fun VoiceResult.withTimings(timings: StageTimings): VoiceResult = when (this) {
    is VoiceResult.Proposed -> copy(timings = timings)
    is VoiceResult.AskBack -> copy(timings = timings)
    is VoiceResult.NotUnderstood -> copy(timings = timings)
}
