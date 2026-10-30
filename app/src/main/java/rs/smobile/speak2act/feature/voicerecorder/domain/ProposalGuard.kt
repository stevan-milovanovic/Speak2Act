package rs.smobile.speak2act.feature.voicerecorder.domain

import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

/** Outcome of fact-checking a model's proposal against what the user actually said. */
sealed interface GuardResult {
    data class Accepted(val transaction: Transaction) : GuardResult

    /** [reason] is a fixed code (safe to log); it never contains user data. */
    data class Rejected(val reason: String) : GuardResult
}

/**
 * Deterministic check of an on-device model's proposal against the transcript: exactly one amount and it's the
 * proposed one, a supported currency that was said (CHF when none was), and every word of the person's name. A
 * reason that wasn't said is dropped. A language model proposes; this class verifies; the user confirms.
 *
 * Notebook 01 filters its training data with a Python port of the same rules (`s2a_data.grounded`).
 */
class ProposalGuard @Inject constructor(private val grammar: VoiceTransactionParser) {

    fun check(proposal: Transaction, transcript: String): GuardResult {
        val amounts = grammar.spokenAmounts(transcript)
        if (amounts.size > 1) return GuardResult.Rejected("amount_ambiguous")   // one transaction per command
        if (amounts.none { it.compareTo(proposal.amount) == 0 }) return GuardResult.Rejected("amount_not_spoken")
        if (grammar.mentionsUnsupportedCurrency(transcript)) return GuardResult.Rejected("currency_unsupported")
        val currencies = grammar.spokenCurrencies(transcript)
        if (currencies.size > 1) return GuardResult.Rejected("currency_ambiguous")
        val said = currencies.singleOrNull() ?: DEFAULT_CURRENCY
        if (proposal.currency != said) return GuardResult.Rejected("currency_mismatch")
        val spoken = transcript.words()
        if (proposal.receiverName.words().let { it.isEmpty() || !spoken.containsAll(it) })
            return GuardResult.Rejected("person_not_spoken")
        val reason = proposal.message?.takeIf { spoken.containsAll(it.words()) }
        return GuardResult.Accepted(proposal.copy(message = reason))
    }

    /** Lower-case, accent-free words: "Stefan's" → [stefan, s], "Milovanović" → [milovanovic]. */
    private fun String.words(): Set<String> =
        Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .split(NON_WORD)
            .filter { it.isNotEmpty() }
            .toSet()

    private companion object {
        val DIACRITICS = Regex("\\p{M}+")
        val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    }
}
