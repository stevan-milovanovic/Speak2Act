package rs.smobile.speak2act.feature.voicerecorder.domain

import java.math.BigDecimal
import java.util.Locale
import javax.inject.Inject

/**
 * Rule-based, on-device parser ("local grammar") that turns a transcribed sentence into a
 * [Transaction].
 *
 * It is deliberately lenient (case-insensitive, tolerant of filler words) and fails closed:
 * [parse] returns `null` whenever the sentence is not a recognizable send/request command or a
 * rule can't decide (e.g. two different amounts), so the caller never acts on a wild guess and
 * can escalate to the cloud instead.
 *
 * English-first; the keyword sets are structured so other languages can be added later without
 * reworking the algorithm.
 */
class VoiceTransactionParser @Inject constructor() {

    fun parse(transcript: String): Transaction? {
        val tokens = tokenize(transcript)
        if (tokens.isEmpty()) return null
        val action = detectAction(tokens) ?: return null
        val amount = detectAmount(tokens) ?: return null
        val receiver = detectReceiver(tokens, action)?.takeIf { it.isNotBlank() } ?: return null
        val currency = detectCurrency(tokens) ?: DEFAULT_CURRENCY
        val message = detectMessage(tokens)
        return Transaction(action, amount, currency, receiver, message)
    }

    /**
     * Every amount spoken anywhere in [transcript], in order, one entry per occurrence ("10 to Ana and 10 to Bo" →
     * [10, 10]) — digits ("12.50", "1'000") or English number words ("twenty-five"), stripped of trailing zeros.
     * Unreadable numbers are skipped. Used to fact-check proposals from a language model.
     */
    fun spokenAmounts(transcript: String): List<BigDecimal> {
        val tokens = tokenize(transcript)
        val amounts = mutableListOf<BigDecimal>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token.any { it.isDigit() }) {
                numericAmount(token)?.let { amounts += it.stripTrailingZeros() }
                index++
                continue
            }
            val group = numberWordGroup(tokens, index)
            if (group != null) {
                group.value?.let { amounts += it.stripTrailingZeros() }
                index = group.end
                continue
            }
            index++
        }
        return amounts
    }

    /** ISO codes of every currency word or symbol in [transcript] ("francs" → CHF, "€" → EUR). */
    fun spokenCurrencies(transcript: String): Set<String> =
        tokenize(transcript).mapNotNull { CurrencyCodes.fromWord(it.cleaned()) }.toSet()

    /** Whether [transcript] names a currency the app doesn't support ("35 yen"). */
    fun mentionsUnsupportedCurrency(transcript: String): Boolean =
        tokenize(transcript).any { CurrencyCodes.isUnsupported(it.cleaned()) }

    /**
     * Normalizes number formatting, then splits on whitespace, keeping the original casing so the
     * message can be kept verbatim.
     *
     * - R2: a `,` `'` or space followed by exactly 3 digits is a thousands separator ("1'000").
     * - R3: currency symbols and codes glued to the amount are split off ("€5", "5€", "CHF12").
     */
    private fun tokenize(transcript: String): List<String> =
        transcript
            .replace(THOUSANDS_SEPARATOR, "")
            .replace(SYMBOL_BEFORE_AMOUNT, "$1 $2")
            .replace(SYMBOL_AFTER_AMOUNT, "$1 $2")
            .replace(CODE_BEFORE_AMOUNT, "$1 $2")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun detectAction(tokens: List<String>): TransactionAction? {
        for (token in tokens) {
            when (token.cleaned()) {
                in SEND_KEYWORDS -> return TransactionAction.SEND
                in REQUEST_KEYWORDS -> return TransactionAction.REQUEST
            }
        }
        return null
    }

    /**
     * Finds the single amount in the command part of the sentence (before the reason clause).
     * Accepts digits ("12.50", "2,80") and number words up to 9,999 ("twenty-five",
     * "two thousand five hundred"). Returns `null` when there is no amount, when a number can't be
     * read safely, or when the sentence holds two different amounts (R5).
     */
    private fun detectAmount(tokens: List<String>): BigDecimal? {
        val command = tokens.subList(0, messageStart(tokens) ?: tokens.size)
        val amounts = mutableListOf<BigDecimal>()
        var index = 0
        while (index < command.size) {
            val token = command[index]
            if (token.any { it.isDigit() }) {
                amounts += numericAmount(token) ?: return null
                index++
                continue
            }
            val group = numberWordGroup(command, index)
            if (group != null) {
                amounts += group.value ?: return null
                index = group.end
                continue
            }
            index++
        }
        val distinct = amounts.map { it.stripTrailingZeros() }.distinct()
        if (distinct.size != 1) return null
        return amounts.first().takeIf { it.signum() > 0 }
    }

    /** Parses a digit token such as "12.50" or "2,80"; `null` when it is not a plain amount. */
    private fun numericAmount(token: String): BigDecimal? {
        val normalized = token.cleaned().replace(',', '.')
        if (!NUMERIC_AMOUNT.matches(normalized)) return null
        return runCatching { BigDecimal(normalized) }.getOrNull()
    }

    private class NumberWordGroup(val value: BigDecimal?, val end: Int)

    /**
     * Reads a run of number words starting at [start] ("one hundred and twenty", "twenty-five").
     * Returns `null` when [start] isn't a number word; a group with a `null` value when the words
     * don't form a valid number up to 9,999 (fail closed).
     */
    private fun numberWordGroup(tokens: List<String>, start: Int): NumberWordGroup? {
        if (numberWords(tokens[start]) == null) return null
        val words = mutableListOf<String>()
        var index = start
        while (index < tokens.size) {
            val parts = numberWords(tokens[index])
            if (parts != null) {
                words += parts
                index++
                // A trailing comma or period ends the number ("ten, to Stefan").
                if (tokens[index - 1].trimEnd().lastOrNull() in SENTENCE_BREAKS) break
                continue
            }
            val isConnector = tokens[index].cleaned() == "and" &&
                    words.lastOrNull() in SCALE_WORDS &&
                    tokens.getOrNull(index + 1)?.let { numberWords(it) } != null
            if (isConnector) {
                index++
                continue
            }
            break
        }
        return NumberWordGroup(evaluateNumberWords(words), index)
    }

    /** The number words in a token ("twenty-five" → [twenty, five]), or `null` if it isn't one. */
    private fun numberWords(token: String): List<String>? {
        val parts = token.cleaned().split('-').filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.any { it !in NUMBER_WORDS }) return null
        return parts
    }

    /**
     * Evaluates English number words up to 9,999. Returns `null` for sequences that are not a
     * well-formed number ("twenty twenty", "five six").
     */
    private fun evaluateNumberWords(words: List<String>): BigDecimal? {
        var total = 0
        var current = 0
        var previous: String? = null
        var seenThousand = false
        for (word in words) {
            val value = NUMBER_WORDS.getValue(word)
            val previousValue = previous?.let { NUMBER_WORDS.getValue(it) }
            val afterScale = previous == null || previous in SCALE_WORDS
            when {
                word == "thousand" -> {
                    if (seenThousand || current > 99) return null
                    total += (if (current == 0) 1 else current) * 1000
                    current = 0
                    seenThousand = true
                }

                word == "hundred" -> {
                    // Only "hundred", "five hundred" or "fifteen hundred"; never "twenty hundred".
                    if (previous == "hundred" || current > 19) return null
                    current = (if (current == 0) 1 else current) * 100
                }

                value == 0 -> if (previous != null) return null

                value < 10 -> { // one … nine
                    val afterTens = previousValue != null && previousValue in 20..90
                    if (!afterScale && !afterTens) return null
                    current += value
                }

                else -> { // ten … nineteen, twenty … ninety
                    if (!afterScale) return null
                    current += value
                }
            }
            previous = word
        }
        val result = total + current
        return if (result <= MAX_SPOKEN_AMOUNT) BigDecimal(result) else null
    }

    /**
     * Detects the spoken currency in the command part and normalizes it to an ISO code (e.g.
     * "CHF", "EUR"). Returns `null` when no currency is present.
     */
    private fun detectCurrency(tokens: List<String>): String? {
        val command = tokens.subList(0, messageStart(tokens) ?: tokens.size)
        for (token in command) {
            CurrencyCodes.fromWord(token.cleaned())?.let { return it }
        }
        return null
    }

    /**
     * Extracts the receiver name. Primary pattern is "... to <Name>" (send) / "... from <Name>"
     * (request). Falls back to the first name-like word after the action verb, skipping filler
     * words (e.g. "pay back Tamara 15"). Stops at a reason clause ("for ...") or any non-name token.
     */
    private fun detectReceiver(tokens: List<String>, action: TransactionAction): String? {
        val preferred = if (action == TransactionAction.REQUEST) "from" else "to"
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
            val isNotName = word in STOP_WORDS ||
                    word in FILLER_WORDS ||
                    word in CurrencyCodes.words ||
                    numberWords(word) != null ||
                    !word.all { it.isNameChar() }
            if (isNotName) {
                // "for …" starts the reason clause: never look for a name inside it.
                if (nameParts.isNotEmpty() || word == "for") break
                index++
                continue
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
     * Returns `null` when there is no reason clause.
     */
    private fun detectMessage(tokens: List<String>): String? {
        val start = messageStart(tokens) ?: return null
        return tokens.subList(start, tokens.size)
            .joinToString(" ")
            .trim()
            .trimEnd(*PUNCTUATION)
            .takeIf { it.isNotBlank() }
    }

    /**
     * Index of the "for" that starts the reason clause. Skips the "for" that introduces the amount
     * (e.g. "ask Tamara for 2.80 francs ...") by requiring the next word to be neither a number nor
     * a currency.
     */
    private fun messageStart(tokens: List<String>): Int? {
        for (index in tokens.indices) {
            if (tokens[index].cleaned() != "for") continue
            val next = tokens.getOrNull(index + 1) ?: continue
            val nextCleaned = next.cleaned()
            val isAmountIntro = nextCleaned.any { it.isDigit() } ||
                    numberWords(nextCleaned) != null ||
                    nextCleaned in CurrencyCodes.words
            if (isAmountIntro) continue
            return index
        }
        return null
    }

    private fun Char.isNameChar(): Boolean = isLetter() || this == '\'' || this == '-'

    /** Lowercased, punctuation-trimmed form used for keyword/structure matching. */
    private fun String.cleaned(): String = trim(*PUNCTUATION).lowercase(Locale.ROOT)

    private companion object {
        val PUNCTUATION = charArrayOf('.', ',', '!', '?', ';', ':', '"', '\u00BB', '\u00AB')
        val SENTENCE_BREAKS = setOf(',', '.', ';', '!', '?')

        val NUMERIC_AMOUNT = Regex("\\d+(?:\\.\\d{1,2})?")

        // R2: separator followed by exactly three digits (and no fourth) between two digits.
        val THOUSANDS_SEPARATOR = Regex("(?<=\\d)[,'\u2019 \u00A0](?=\\d{3}(?!\\d))")

        // R3: currency symbols / codes glued to the amount.
        val SYMBOL_BEFORE_AMOUNT = Regex("([€$£])(\\d)")
        val SYMBOL_AFTER_AMOUNT = Regex("(\\d)([€$£])")
        val CODE_BEFORE_AMOUNT = Regex("(?i)\\b(chf|eur|usd|gbp)(\\d)")

        const val MAX_SPOKEN_AMOUNT = 9_999

        val SEND_KEYWORDS =
            setOf("send", "sent", "sending", "pay", "paid", "transfer", "give", "wire")
        val REQUEST_KEYWORDS = setOf("request", "requested", "ask", "charge", "collect")

        // Words that may follow the amount/name but are not part of the recipient name.
        val STOP_WORDS = setOf(
            "for", "because", "since", "so", "as", "with", "the", "a", "an", "and", "of",
            "to", "from", "please",
        )

        // R4: words that can sit between the verb and the name ("pay back Tamara", "send me …").
        val FILLER_WORDS = setOf(
            "back", "me", "us", "him", "her", "them", "over", "just", "quickly", "money",
            "some", "i", "we", "you",
        )

        val SCALE_WORDS = setOf("hundred", "thousand")

        val NUMBER_WORDS = mapOf(
            "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11,
            "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
            "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
            "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80,
            "ninety" to 90, "hundred" to 100, "thousand" to 1000,
        )
    }
}
