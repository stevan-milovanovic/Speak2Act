package rs.smobile.speak2act.feature.voicerecorder.data.llm

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolCall
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import rs.smobile.speak2act.core.ai.TransactionContract
import rs.smobile.speak2act.feature.voicerecorder.data.toTransactionOrNull
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction

/** The shared contract as a LiteRT-LM tool. LiteRT-LM never runs it: automaticToolCalling is off. */
internal object TransactionTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String = TransactionContract.toolJson
    override fun execute(paramsJsonString: String): String =
        error("executeTransaction is proposed to the user, never executed by the model runtime")
}

/**
 * The proposal in a reply: exactly one tool call, to executeTransaction, whose arguments map to a [Transaction].
 * Anything else (no call, two calls, another name) is `null` — two calls mean two transactions, and the app takes
 * one per command.
 */
internal fun List<ToolCall>.toProposalOrNull(): Transaction? =
    singleOrNull()
        ?.takeIf { it.name == TransactionContract.FUNCTION_NAME }
        ?.arguments?.toJsonArguments()?.toTransactionOrNull()

/** LiteRT-LM tool-call arguments → the JSON map that the shared [toTransactionOrNull] mapper reads. */
internal fun Map<String, Any?>.toJsonArguments(): Map<String, JsonElement> = mapValues { (_, value) ->
    when (value) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value.toString())   // Gson's LazilyParsedNumber keeps the digits ("12.50")
        else -> JsonPrimitive(value.toString())
    }
}
