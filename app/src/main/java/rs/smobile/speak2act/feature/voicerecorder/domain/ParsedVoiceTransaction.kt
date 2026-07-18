package rs.smobile.speak2act.feature.voicerecorder.domain

import java.math.BigDecimal

/** The kind of transaction action recognised in a spoken command. */
enum class VoiceTransactionAction {
    SEND,
    REQUEST,
}

/**
 * A structured transaction intent extracted from a transcribed sentence. [receiverName] is the
 * raw name as spoken. [currency] is the normalized ISO code of the spoken currency (e.g. "CHF",
 * "EUR"), or `null` when none was spoken. [message] is the optional spoken reason clause (e.g.
 * "for the pizza"), kept verbatim, or `null` when absent.
 */
data class ParsedVoiceTransaction(
    val action: VoiceTransactionAction,
    val amount: BigDecimal,
    val receiverName: String,
    val currency: String? = null,
    val message: String? = null,
)
