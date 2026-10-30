package rs.smobile.speak2act.core.config

import rs.smobile.speak2act.BuildConfig

/**
 * Internal feature flag: which conference talk this build demonstrates, so each talk's demos can be
 * recorded independently from the same code base.
 *
 * Chosen at build time with `DEMO_TALK=1|2|3` in `local.properties` or `-PdemoTalk=1|2|3`.
 */
enum class DemoTalk {
    /**
     * Talk 1, "AI Agents on Android": the recording goes straight to Gemini (function calling),
     * then the user confirms. No on-device Whisper, no consent dialog, no route badge.
     */
    AGENTS,

    /**
     * Talk 2, "Edge AI and Serverless": hybrid cascade (Whisper and the local grammar first,
     * Gemini on the transcript as fallback), consent dialog, route badge and "Heard" line.
     */
    EDGE_AND_SERVERLESS,

    /**
     * Talk 3, "Running AI on Your Phone": talk 2's cascade plus a fine-tuned FunctionGemma on the
     * device between the grammar and the cloud.
     */
    ON_DEVICE_LLM;

    /** Whether the UI shows the hybrid details of talks 2 and 3 (consent, route badge, "Heard" line). */
    val showsHybridDetails: Boolean get() = this != AGENTS

    /** Whether the on-device language model runs between the grammar and the cloud (talk 3). */
    val usesOnDeviceLlm: Boolean get() = this == ON_DEVICE_LLM

    companion object {
        /** The talk selected for this build. */
        val current: DemoTalk get() = when (BuildConfig.DEMO_TALK) {
            1 -> AGENTS
            3 -> ON_DEVICE_LLM
            else -> EDGE_AND_SERVERLESS
        }
    }
}
