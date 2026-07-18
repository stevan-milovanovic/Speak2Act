package rs.smobile.speak2act.feature.voicerecorder.domain

import java.math.BigDecimal
import java.util.Locale

/**
 * Rule-based, on-device parser that turns a transcribed sentence into a [ParsedVoiceTransaction].
 *
 * It is deliberately lenient (case-insensitive, tolerant of filler words) and fails closed:
 * [parse] returns `null` whenever the sentence is not a recognizable send/request command, so the
 * caller never acts on a wild guess.
 *
 * English-first; the keyword sets are structured so other languages can be added later without
 * reworking the algorithm.
 */
class VoiceTransactionParser {

    fun parse(transcript: String): ParsedVoiceTransaction? {
        val tokens = tokenize(transcript)
        if (tokens.isEmpty()) return null

        val action = detectAction(tokens) ?: return null
        val amount = detectAmount(tokens) ?: return null
        val receiver = detectReceiver(tokens, action)?.takeIf { it.isNotBlank() } ?: return null
        val currency = detectCurrency(tokens)
        val message = detectMessage(tokens)

        return ParsedVoiceTransaction(action, amount, receiver, currency, message)
    }

    /** Splits on whitespace, keeping the original casing so the message can be kept verbatim. */
    private fun tokenize(transcript: String): List<String> =
        transcript.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun detectAction(tokens: List<String>): VoiceTransactionAction? {
        for (token in tokens) {
            when (token.cleaned()) {
                in SEND_KEYWORDS -> return VoiceTransactionAction.SEND
                in REQUEST_KEYWORDS -> return VoiceTransactionAction.REQUEST
            }
        }
        return null
    }

    private fun detectAmount(tokens: List<String>): BigDecimal? {
        for (token in tokens) {
            val numeric = NUMERIC_AMOUNT.find(token.replace(',', '.'))?.value
            if (numeric != null) {
                return runCatching { BigDecimal(numeric) }.getOrNull()?.takeIf { it.signum() > 0 }
            }
        }
        // Fallback: a single spelled-out number word (e.g. "ten").
        for (token in tokens) {
            NUMBER_WORDS[token.cleaned()]?.let { return BigDecimal(it) }
        }
        return null
    }

    /**
     * Detects the spoken currency and normalizes it to an ISO code (e.g. "CHF", "EUR"). Returns
     * `null` when no currency word is present.
     */
    private fun detectCurrency(tokens: List<String>): String? {
        for (token in tokens) {
            CURRENCY_CODES[token.cleaned()]?.let { return it }
        }
        return null
    }

    /**
     * Extracts the receiver name. Primary pattern is "... to <Name>" (send) / "... from <Name>"
     * (request). Falls back to the first name-like word right after the action verb (e.g.
     * "pay Markus 15"). Stops at a reason clause ("for ...") or any non-name token.
     */
    private fun detectReceiver(tokens: List<String>, action: VoiceTransactionAction): String? {
        val preferred = if (action == VoiceTransactionAction.REQUEST) "from" else "to"
        prepositionReceiver(tokens, preferred)?.let { return it }
        val other = if (preferred == "to") "from" else "to"
        prepositionReceiver(tokens, other)?.let { return it }
        return verbAdjacentReceiver(tokens)
    }

    private fun prepositionReceiver(tokens: List<String>, preposition: String): String? {
        val index = tokens.indexOfFirst { it.cleaned() == preposition }
        if (index == -1) return null
        return collectName(tokens, index + 1)
    }

    private fun verbAdjacentReceiver(tokens: List<String>): String? {
        val verbIndex = tokens.indexOfFirst {
            val word = it.cleaned()
            word in SEND_KEYWORDS || word in REQUEST_KEYWORDS
        }
        if (verbIndex == -1) return null
        return collectName(tokens, verbIndex + 1)
    }

    private fun collectName(tokens: List<String>, startIndex: Int): String? {
        val nameParts = mutableListOf<String>()
        var index = startIndex
        while (index < tokens.size) {
            val word = tokens[index].cleaned()
            if (word.isEmpty()) {
                index++
                continue
            }
            if (word in STOP_WORDS || word in CURRENCY_WORDS || !word.all { it.isNameChar() }) {
                if (nameParts.isNotEmpty()) break else {
                    index++; continue
                }
            }
            nameParts.add(word)
            index++
        }
        if (nameParts.isEmpty()) return null
        return nameParts.joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    /**
     * Extracts the spoken reason as the verbatim "for ..." clause to the end of the sentence.
     * Skips the "for" that introduces the amount (e.g. "ask Tamara for 2.80 francs ...") by
     * requiring the word right after "for" to be neither a number nor a currency word. Returns
     * `null` when there is no reason clause.
     */
    private fun detectMessage(tokens: List<String>): String? {
        for (index in tokens.indices) {
            if (tokens[index].cleaned() != "for") continue
            val next = tokens.getOrNull(index + 1) ?: continue
            val nextCleaned = next.cleaned()
            val isAmountIntro = NUMERIC_AMOUNT.matches(next.replace(',', '.')) ||
                    NUMBER_WORDS.containsKey(nextCleaned) ||
                    nextCleaned in CURRENCY_WORDS
            if (isAmountIntro) continue

            return tokens.subList(index, tokens.size)
                .joinToString(" ")
                .trim()
                .trimEnd(*PUNCTUATION)
                .takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun Char.isNameChar(): Boolean = isLetter() || this == '\'' || this == '-'

    /** Lowercased, punctuation-trimmed form used for keyword/structure matching. */
    private fun String.cleaned(): String = trim(*PUNCTUATION).lowercase(Locale.ROOT)

    private companion object {
        val PUNCTUATION = charArrayOf('.', ',', '!', '?', ';', ':', '"', '\u00BB', '\u00AB')

        val NUMERIC_AMOUNT = Regex("\\d+(?:\\.\\d{1,2})?")

        val SEND_KEYWORDS =
            setOf("send", "sent", "sending", "pay", "paid", "transfer", "give", "wire")
        val REQUEST_KEYWORDS = setOf("request", "requested", "ask", "charge", "collect")

        // Words that may follow the amount/name but are not part of the recipient name.
        val STOP_WORDS = setOf(
            "for", "because", "since", "so", "as", "with", "the", "a", "an", "and", "of",
            "to", "from", "please",
        )

        // Spoken currency words mapped to their normalized ISO code.
        val CURRENCY_CODES = mapOf(
            "francs" to "CHF", "franc" to "CHF", "franks" to "CHF", "chf" to "CHF",
            "fr" to "CHF", "fr." to "CHF", "swiss" to "CHF", "rappen" to "CHF",
            "euro" to "EUR", "euros" to "EUR", "eur" to "EUR",
            "dollar" to "USD", "$" to "USD", "dollars" to "USD", "usd" to "USD", "bucks" to "USD",
            "pound" to "GBP", "pounds" to "GBP", "gbp" to "GBP", "quid" to "GBP",
        )

        // Currency-indicating words that must not be treated as part of a recipient name.
        val CURRENCY_WORDS = CURRENCY_CODES.keys

        val NUMBER_WORDS = mapOf(
            "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
            "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
            "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
            "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80,
            "ninety" to 90, "hundred" to 100,
        )
    }
}
