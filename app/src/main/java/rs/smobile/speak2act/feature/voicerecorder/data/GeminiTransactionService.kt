package rs.smobile.speak2act.feature.voicerecorder.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rs.smobile.speak2act.BuildConfig
import rs.smobile.speak2act.core.ai.AiModel
import rs.smobile.speak2act.core.ai.AiModels
import rs.smobile.speak2act.core.config.RoutingConfig
import rs.smobile.speak2act.feature.voicerecorder.domain.CloudTransactionService
import rs.smobile.speak2act.feature.voicerecorder.domain.InferenceRoute
import rs.smobile.speak2act.feature.voicerecorder.domain.VoiceResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CloudTransactionService] backed by Gemini through Firebase AI Logic.
 *
 * Each model is created once, lazily (so a new model name applies on the next app start), and each
 * request builds its own immutable prompt. Exceptions (network, quota, App Check) propagate to the
 * caller.
 */
@Singleton
class GeminiTransactionService @Inject constructor(
    private val routing: RoutingConfig,
) : CloudTransactionService {

    private companion object {
        private const val TAG = "Speak2ActRoute"
        private const val AUDIO_PROMPT = "Analyze the audio and extract transaction instructions"
        private const val WAV_MIME_TYPE = "audio/wav"
    }

    private val transcriptModel: GenerativeModel by lazy { createModel(AiModels.TranscriptToTransaction) }
    private val audioModel: GenerativeModel by lazy { createModel(AiModels.SpeechToTransaction) }

    override suspend fun fromTranscript(transcript: String): VoiceResult =
        generate(transcriptModel, InferenceRoute.CLOUD_TEXT, transcript) {
            text("Transcript: $transcript")
        }

    override suspend fun fromAudio(wav: ByteArray): VoiceResult =
        generate(audioModel, InferenceRoute.CLOUD_AUDIO, transcript = null) {
            text(AUDIO_PROMPT)
            inlineData(wav, WAV_MIME_TYPE)
        }

    private suspend fun generate(
        model: GenerativeModel,
        route: InferenceRoute,
        transcript: String?,
        prompt: Content.Builder.() -> Unit,
    ): VoiceResult = withContext(Dispatchers.IO) {
        val response = model.generateContent(content(init = prompt))
        if (BuildConfig.DEBUG) logUsage(route, response)
        response.toVoiceResult(route, transcript)
    }

    private fun GenerateContentResponse.toVoiceResult(route: InferenceRoute, transcript: String?): VoiceResult {
        val call = functionCalls.firstOrNull { it.name == AiModels.EXECUTE_TRANSACTION_FUNCTION_NAME }
        call?.args?.toTransactionOrNull()?.let {
            return VoiceResult.Proposed(it, route, transcript)
        }
        text?.trim()?.takeIf { it.isNotBlank() }?.let {
            return VoiceResult.AskBack(it, route)
        }
        return VoiceResult.NotUnderstood(transcript.orEmpty())
    }

    private fun createModel(model: AiModel): GenerativeModel = with(model) {
        Firebase.ai(backend = GenerativeBackend.googleAI(), useLimitedUseAppCheckTokens = true).generativeModel(
            modelName = routing.modelName,
            generationConfig = generationConfig,
            safetySettings = safetySettings,
            systemInstruction = content { text(systemInstruction) },
            tools = tools
        )
    }

    /** Debug builds only: token usage per call, for the measurements on the slides. */
    private fun logUsage(route: InferenceRoute, response: GenerateContentResponse) {
        val usage = response.usageMetadata ?: return
        val promptByModality = usage.promptTokensDetails.joinToString { "${it.modality}=${it.tokenCount}" }
        Log.d(
            TAG,
            "usage route=$route model=${routing.modelName} prompt=${usage.promptTokenCount} " +
                    "[$promptByModality] candidates=${usage.candidatesTokenCount} " +
                    "thoughts=${usage.thoughtsTokenCount} total=${usage.totalTokenCount}"
        )
    }
}
