package rs.smobile.speak2act.core.config

import rs.smobile.speak2act.BuildConfig

/**
 * Internal feature flag: which conference talk this build demonstrates, so each talk's demos can be
 * recorded independently from the same code base.
 *
 * Chosen at build time with `DEMO_TALK=1|2` in `local.properties` or `-PdemoTalk=1|2`.
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
    EDGE_AND_SERVERLESS;

    /** Whether the UI shows talk 2's hybrid details (consent, route badge, "Heard" line). */
    val showsHybridDetails: Boolean get() = this == EDGE_AND_SERVERLESS

    companion object {
        /** The talk selected for this build. */
        val current: DemoTalk get() = if (BuildConfig.DEMO_TALK == 1) AGENTS else EDGE_AND_SERVERLESS
    }
}
