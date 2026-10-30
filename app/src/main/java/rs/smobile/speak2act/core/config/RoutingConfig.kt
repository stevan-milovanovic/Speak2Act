package rs.smobile.speak2act.core.config

import rs.smobile.speak2act.core.privacy.CloudConsentStore
import javax.inject.Inject
import javax.inject.Singleton

/** How spoken commands are routed between the device and the cloud. */
enum class VoiceRoutingMode { EDGE_ONLY, EDGE_THEN_CLOUD, CLOUD_AUDIO }

/** Remote Config key for the Gemini model name. */
const val MODEL_NAME = "model_name"

/** Remote Config key for the voice route ([VoiceRoutingMode] name). */
const val VOICE_ROUTE = "voice_route"

/** Model used when no remote value is available. */
const val DEFAULT_MODEL_NAME = "gemini-3.5-flash-lite"

/** Talk 1's model ("Gemini 3 Flash (preview)" on slides S8, S13 and A3). */
const val TALK_1_MODEL_NAME = "gemini-3.5-flash-lite"

/** Route used when no (valid) remote value is available. */
val DEFAULT_VOICE_ROUTE = VoiceRoutingMode.EDGE_THEN_CLOUD

/**
 * Model name and routing for the AI features. Kept out of the code paths that use them so they can
 * be changed without a release.
 */
interface RoutingConfig {
    val modelName: String

    /** Effective route: EDGE_ONLY until the user has granted cloud consent. */
    val route: VoiceRoutingMode

    /** Whether the on-device language model runs between the grammar and the cloud. */
    val localLlmEnabled: Boolean
}

/** Parses a [VoiceRoutingMode] name; unknown or blank values fall back to [DEFAULT_VOICE_ROUTE]. */
fun parseRoute(value: String?): VoiceRoutingMode =
    VoiceRoutingMode.entries.find { it.name == value?.trim() } ?: DEFAULT_VOICE_ROUTE

/**
 * [RoutingConfig] with built-in defaults.
 *
 * TODO(H4): replace with a Remote Config-backed implementation (`FirebaseRoutingConfig`, keys
 *  [MODEL_NAME] and [VOICE_ROUTE], later also `local_llm_enabled`) once the `firebase-config`
 *  dependency is approved.
 */
@Singleton
class DefaultRoutingConfig @Inject constructor(
    private val consent: CloudConsentStore,
    private val demoTalk: DemoTalk,
) : RoutingConfig {

    override val modelName: String
        get() = when (demoTalk) {
            DemoTalk.AGENTS -> TALK_1_MODEL_NAME
            DemoTalk.EDGE_AND_SERVERLESS -> DEFAULT_MODEL_NAME
            DemoTalk.ON_DEVICE_LLM -> DEFAULT_MODEL_NAME
        }

    override val route: VoiceRoutingMode
        get() = when {
            // Talk 1's design: the recording itself goes to Gemini.
            demoTalk == DemoTalk.AGENTS -> VoiceRoutingMode.CLOUD_AUDIO
            consent.granted.value != true -> VoiceRoutingMode.EDGE_ONLY
            else -> DEFAULT_VOICE_ROUTE
        }

    override val localLlmEnabled: Boolean get() = demoTalk.usesOnDeviceLlm
}
