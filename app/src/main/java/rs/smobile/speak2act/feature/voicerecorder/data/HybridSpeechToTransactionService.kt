package rs.smobile.speak2act.feature.voicerecorder.data

import kotlinx.coroutines.CancellationException
import rs.smobile.speak2act.core.config.RoutingConfig
import rs.smobile.speak2act.core.config.VoiceRoutingMode.CLOUD_AUDIO
import rs.smobile.speak2act.core.config.VoiceRoutingMode.EDGE_ONLY
import rs.smobile.speak2act.core.logging.RouteLogger
import rs.smobile.speak2act.core.network.NetworkMonitor
import rs.smobile.speak2act.feature.voicerecorder.domain.CloudTransactionService
import rs.smobile.speak2act.feature.voicerecorder.domain.GuardResult
import rs.smobile.speak2act.feature.voicerecorder.domain.InferenceRoute.EDGE
import rs.smobile.speak2act.feature.voicerecorder.domain.InferenceRoute.EDGE_LLM
import rs.smobile.speak2act.feature.voicerecorder.domain.OnDeviceTransactionModel
import rs.smobile.speak2act.feature.voicerecorder.domain.ProposalGuard
import rs.smobile.speak2act.feature.voicerecorder.domain.SpeechToTransactionService
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction
import rs.smobile.speak2act.feature.voicerecorder.domain.Transcriber
import rs.smobile.speak2act.feature.voicerecorder.domain.VoiceResult
import rs.smobile.speak2act.feature.voicerecorder.domain.VoiceTransactionParser
import rs.smobile.speak2act.feature.voicerecorder.domain.withTimings
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * Hybrid voice pipeline with three local stages before the cloud: Whisper, the local grammar and —
 * when enabled (talk 3) — an on-device language model whose proposal is fact-checked against the
 * transcript. When none of them can decide, only the transcript text goes to Gemini. Every stage
 * fails closed, and the user still confirms every transaction.
 */
@Singleton
class HybridSpeechToTransactionService @Inject constructor(
    private val whisper: Transcriber,
    private val grammar: VoiceTransactionParser,
    private val onDeviceModel: OnDeviceTransactionModel,
    private val guard: ProposalGuard,
    private val cloud: CloudTransactionService,
    private val routing: RoutingConfig,
    private val network: NetworkMonitor,
    private val logger: RouteLogger,
) : SpeechToTransactionService {

    override suspend fun parse(audio: FloatArray, onModelProgress: (Float) -> Unit): VoiceResult {
        val timer = StageTimer()                       // TimeSource.Monotonic
        val result = route(audio, onModelProgress, timer)
        return result.withTimings(timer.finish()).also(::logRoute)   // route + durations, no PII
    }

    private suspend fun route(audio: FloatArray, onModelProgress: (Float) -> Unit, timer: StageTimer): VoiceResult {
        if (routing.route == CLOUD_AUDIO && network.isOnline())
            return timer.cloud { cloud.fromAudio(audio.toWav()) }          // ☁️ A/B
        val transcript = timer.transcribe { whisper.transcribe(audio, onModelProgress) }
        timer.grammar { grammar.parse(transcript) }?.let {
            return VoiceResult.Proposed(it, EDGE, transcript)
        }
        if (routing.localLlmEnabled) {
            timer.llm { onDeviceProposal(transcript) }?.let {
                return VoiceResult.Proposed(it, EDGE_LLM, transcript)      // 🧠 on device
            }
        }
        if (routing.route == EDGE_ONLY || !network.isOnline())
            return VoiceResult.NotUnderstood(transcript)
        return timer.cloud { cloud.fromTranscript(transcript) }            // ☁️ text
    }

    /** The on-device proposal after the guard, or null: no call, rejected or failed (fail closed). */
    private suspend fun onDeviceProposal(transcript: String): Transaction? {
        val proposal = try {
            onDeviceModel.propose(transcript)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.log("llm_failed error=${e::class.simpleName}")
            null
        } ?: return null
        return when (val verdict = guard.check(proposal, transcript)) {
            is GuardResult.Accepted -> verdict.transaction
            is GuardResult.Rejected -> null.also { logger.log("llm_rejected reason=${verdict.reason}") }
        }
    }

    override fun release() {
        whisper.release()
        onDeviceModel.release()
    }

    /** Logs the route and the stage durations in every build. Never logs transcripts or values. */
    private fun logRoute(result: VoiceResult) {
        val outcome = when (result) {
            is VoiceResult.Proposed -> "proposed source=${result.source}"
            is VoiceResult.AskBack -> "ask_back source=${result.source}"
            is VoiceResult.NotUnderstood -> "not_understood"
        }
        val t = result.timings
        logger.log(
            "$outcome mode=${routing.route} transcribe=${t.transcribe.ms()} grammar=${t.grammar.us()} " +
                    "llm=${t.llm.ms()} cloud=${t.cloud.ms()} total=${t.total.inWholeMilliseconds}ms"
        )
    }

    private fun Duration?.ms(): String = this?.let { "${it.inWholeMilliseconds}ms" } ?: "-"
    private fun Duration?.us(): String = this?.let { "${it.inWholeMicroseconds}us" } ?: "-"
}
