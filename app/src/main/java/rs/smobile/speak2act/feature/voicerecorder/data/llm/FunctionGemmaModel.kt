package rs.smobile.speak2act.feature.voicerecorder.data.llm

import rs.smobile.speak2act.BuildConfig
import rs.smobile.speak2act.core.ai.AiModels
import rs.smobile.speak2act.core.ai.TransactionContract
import rs.smobile.speak2act.core.model.ModelSpec

/** Pinned model files for the on-device language model. [current] is chosen with LOCAL_LLM (plan §8.10). */
object FunctionGemmaModel {
    /**
     * The fine-tuned model from the Colab guide, published on Hugging Face (Gemma Terms of Use) and pinned by size
     * and SHA-256 (plan §9.4). Downloaded once, on an unmetered network, when the recorder screen opens.
     */
    val TUNED = ModelSpec(
        fileName = "speak2act-functiongemma-v1.litertlm",
        url = "https://huggingface.co/stevan-milovanovic/speak2act-functiongemma-270m/resolve/main/speak2act-functiongemma-v1.litertlm",
        sizeBytes = 284_117_632L,
        sha256 = "0d5471e79b6677ac7b7a24232ed65326076345d3ec14cc9cc6e94e56d9001f63",
        isStale = { it.startsWith("speak2act-functiongemma-") && it.endsWith(".litertlm") },
    )

    /** Untuned FunctionGemma converted by notebook 02 — the "before" of the comparison (plan §8.10). */
    val BASE = ModelSpec("functiongemma-270m-base.litertlm", url = "", sizeBytes = 0L, sha256 = "")

    /** Gemma 4 E2B (Apache 2.0, ungated) — the "generalist" comparison (plan §8.10). */
    val GEMMA4_E2B = ModelSpec(
        fileName = "gemma-4-E2B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
    )

    val current: ModelSpec
        get() = when (BuildConfig.LOCAL_LLM) {
            "base" -> BASE
            "gemma4" -> GEMMA4_E2B
            else -> TUNED
        }

    /** FunctionGemma was trained on its standard prompt; Gemma 4 is a general model and gets the cloud's. */
    fun systemPrompt(spec: ModelSpec): String =
        if (spec == GEMMA4_E2B) AiModels.TranscriptToTransaction.systemInstruction
        else TransactionContract.ON_DEVICE_SYSTEM_PROMPT
}
