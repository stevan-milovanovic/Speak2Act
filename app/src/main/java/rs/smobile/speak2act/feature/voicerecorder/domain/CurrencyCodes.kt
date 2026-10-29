package rs.smobile.speak2act.feature.voicerecorder.domain

import java.util.Locale

/**
 * Maps spoken currency words and symbols to ISO 4217 codes. Shared by the local grammar and the
 * cloud response mapper.
 */
object CurrencyCodes {

    private val WORDS = mapOf(
        "francs" to "CHF", "franc" to "CHF", "franks" to "CHF", "chf" to "CHF",
        "fr" to "CHF", "fr." to "CHF", "swiss" to "CHF", "rappen" to "CHF",
        "euro" to "EUR", "euros" to "EUR", "eur" to "EUR", "€" to "EUR",
        "dollar" to "USD", "dollars" to "USD", "usd" to "USD", "bucks" to "USD", "$" to "USD",
        "pound" to "GBP", "pounds" to "GBP", "gbp" to "GBP", "quid" to "GBP", "£" to "GBP",
    )

    /** Every word or symbol this map recognizes (lowercase). */
    val words: Set<String> = WORDS.keys

    /** ISO code for a lowercase currency word or symbol, or null when it isn't one. */
    fun fromWord(word: String): String? = WORDS[word.lowercase(Locale.ROOT)]

    /**
     * Normalizes a currency value: known words and symbols become ISO codes ("euros" → "EUR"),
     * three-letter codes are upper-cased, and anything else comes back unchanged.
     */
    fun normalize(value: String): String {
        val trimmed = value.trim()
        fromWord(trimmed)?.let { return it }
        return if (trimmed.length == 3 && trimmed.all { it.isLetter() }) {
            trimmed.uppercase(Locale.ROOT)
        } else {
            value
        }
    }
}
