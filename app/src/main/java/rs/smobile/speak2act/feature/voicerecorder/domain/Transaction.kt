package rs.smobile.speak2act.feature.voicerecorder.domain

import java.math.BigDecimal

/** What the user wants to do with the money. */
enum class TransactionAction { SEND, REQUEST, SPLIT }

/** Currency used when the user doesn't say one. */
const val DEFAULT_CURRENCY = "CHF"

/**
 * A proposed money transaction, produced either on the device (local grammar) or in the cloud
 * (Gemini function calling). The user must confirm it before it is executed.
 */
data class Transaction(
    val action: TransactionAction,
    val amount: BigDecimal,
    val currency: String,          // ISO 4217 code, e.g. "CHF"
    val receiverName: String,      // as spoken
    val message: String? = null,   // the verbatim reason, e.g. "for the taxi bill"
)
