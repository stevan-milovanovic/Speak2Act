package rs.smobile.speak2act.feature.voicerecorder.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import rs.smobile.speak2act.core.ai.AiModels.ACTION_PARAM
import rs.smobile.speak2act.core.ai.AiModels.AMOUNT_PARAM
import rs.smobile.speak2act.core.ai.AiModels.CURRENCY_PARAM
import rs.smobile.speak2act.core.ai.AiModels.DESCRIPTION_PARAM
import rs.smobile.speak2act.core.ai.AiModels.PERSON_PARAM
import rs.smobile.speak2act.feature.voicerecorder.domain.CurrencyCodes
import rs.smobile.speak2act.feature.voicerecorder.domain.DEFAULT_CURRENCY
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction
import rs.smobile.speak2act.feature.voicerecorder.domain.TransactionAction

/**
 * Maps the arguments of an `executeTransaction` function call to a [Transaction], or `null` when a
 * required argument is missing or invalid.
 */
internal fun Map<String, JsonElement>.toTransactionOrNull(): Transaction? {
    val action = when (string(ACTION_PARAM)?.lowercase()) {
        "pay" -> TransactionAction.SEND
        "request" -> TransactionAction.REQUEST
        "split" -> TransactionAction.SPLIT
        else -> return null
    }
    val amount = string(AMOUNT_PARAM)?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
    val person = string(PERSON_PARAM) ?: return null
    val currency = string(CURRENCY_PARAM)?.let(CurrencyCodes::normalize) ?: DEFAULT_CURRENCY
    return Transaction(action, amount, currency, person, string(DESCRIPTION_PARAM))
}

/** JsonPrimitive content; JSON null and blank values count as missing. */
private fun Map<String, JsonElement>.string(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.takeUnless { it is JsonNull }
        ?.content
        ?.trim()
        ?.takeIf { it.isNotBlank() }
