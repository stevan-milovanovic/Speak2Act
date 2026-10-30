package rs.smobile.speak2act.core.ai

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The `executeTransaction` function, shared by Gemini in the cloud ([AiModels]) and the on-device model.
 * The fine-tuned FunctionGemma was trained on exactly [toolJson]: change both together (see
 * ml/functiongemma/execute_transaction.json and the Colab guide).
 */
object TransactionContract {
    const val FUNCTION_NAME = "executeTransaction"
    const val FUNCTION_DESCRIPTION = "Prepares a money transaction for the user to confirm."
    const val ACTION_PARAM = "action"
    const val AMOUNT_PARAM = "amount"
    const val CURRENCY_PARAM = "currency"
    const val PERSON_PARAM = "person"
    const val DESCRIPTION_PARAM = "description"
    val ACTIONS = listOf("pay", "request", "split")
    const val ACTION_DESCRIPTION = "Type of the transaction."
    const val AMOUNT_DESCRIPTION = "Amount of the transaction."
    const val CURRENCY_DESCRIPTION = "Currency of the transaction, e.g. CHF or EUR."
    const val PERSON_DESCRIPTION = "Contact to whom the transaction is addressed."
    const val REASON_DESCRIPTION = "The reason for the transaction."

    /** FunctionGemma's standard developer prompt — the fine-tuning data uses the same string. */
    const val ON_DEVICE_SYSTEM_PROMPT = "You are a model that can do function calling with the following functions"

    /**
     * JSON schema of the function, as LiteRT-LM tools and the fine-tuning data expect it. Keys are in alphabetical
     * order at every level: LiteRT-LM renders the declaration in the JSON's own key order, and the Hugging Face chat
     * template sorts keys, so the notebooks can compare both renderings.
     */
    val toolJson: String = buildJsonObject {
        put("description", FUNCTION_DESCRIPTION)
        put("name", FUNCTION_NAME)
        putJsonObject("parameters") {
            putJsonObject("properties") {
                putJsonObject(ACTION_PARAM) {
                    put("description", ACTION_DESCRIPTION)
                    putJsonArray("enum") { ACTIONS.forEach { add(it) } }
                    put("type", "string")
                }
                putJsonObject(AMOUNT_PARAM) {
                    put("description", AMOUNT_DESCRIPTION)
                    put("type", "number")
                }
                putJsonObject(CURRENCY_PARAM) {
                    put("description", CURRENCY_DESCRIPTION)
                    put("type", "string")
                }
                putJsonObject(DESCRIPTION_PARAM) {
                    put("description", REASON_DESCRIPTION)
                    put("type", "string")
                }
                putJsonObject(PERSON_PARAM) {
                    put("description", PERSON_DESCRIPTION)
                    put("type", "string")
                }
            }
            putJsonArray("required") {
                listOf(ACTION_PARAM, AMOUNT_PARAM, CURRENCY_PARAM, PERSON_PARAM).forEach { add(it) }
            }
            put("type", "object")
        }
    }.toString()
}
