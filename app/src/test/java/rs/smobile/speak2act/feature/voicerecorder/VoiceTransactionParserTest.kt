package rs.smobile.speak2act.feature.voicerecorder

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.smobile.speak2act.feature.voicerecorder.domain.DEFAULT_CURRENCY
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction
import rs.smobile.speak2act.feature.voicerecorder.domain.TransactionAction
import rs.smobile.speak2act.feature.voicerecorder.domain.VoiceTransactionParser

class VoiceTransactionParserTest {

    private val parser = VoiceTransactionParser()

    @Test
    fun parsesSendCommandWithReasonClause() {
        val result = parser.parse("Send 10 francs to Stefan for the pizza")

        assertEquals(TransactionAction.SEND, result?.action)
        assertEquals(BigDecimal("10"), result?.amount)
        assertEquals("Stefan", result?.receiverName)
        assertEquals("CHF", result?.currency)
        assertEquals("for the pizza", result?.message)
    }

    @Test
    fun parsesRequestWithAmountIntroAndReasonClause() {
        val result = parser.parse("Ask Tamara for 2.80 francs for the train ticket")

        assertEquals(TransactionAction.REQUEST, result?.action)
        assertEquals(BigDecimal("2.80"), result?.amount)
        assertEquals("Tamara", result?.receiverName)
        assertEquals("for the train ticket", result?.message)
    }

    @Test
    fun keepsMessageVerbatimWithTrailingPunctuationTrimmed() {
        val result = parser.parse("Send 5 to Stefan for the Movie Night!")

        assertEquals("for the Movie Night", result?.message)
    }

    @Test
    fun hasNoMessageWhenReasonClauseAbsent() {
        val result = parser.parse("send 10 to Stefan")

        assertEquals("Stefan", result?.receiverName)
        assertNull(result?.message)
    }

    @Test
    fun parsesFranksCurrencyHomophone() {
        val result = parser.parse("send 10 franks to Stefan")

        assertEquals(TransactionAction.SEND, result?.action)
        assertEquals(BigDecimal("10"), result?.amount)
        assertEquals("Stefan", result?.receiverName)
        assertEquals("CHF", result?.currency)
    }

    @Test
    fun parsesRequestCommandWithFrom() {
        val result = parser.parse("Request 25 from Maria")

        assertEquals(TransactionAction.REQUEST, result?.action)
        assertEquals(BigDecimal("25"), result?.amount)
        assertEquals("Maria", result?.receiverName)
    }

    @Test
    fun parsesDecimalAmountWithCommaSeparator() {
        val result = parser.parse("send 2,80 to Tamara")

        assertEquals(BigDecimal("2.80"), result?.amount)
        assertEquals("Tamara", result?.receiverName)
    }

    @Test
    fun parsesDecimalAmountWithDotSeparator() {
        val result = parser.parse("pay 12.50 chf to John")

        assertEquals(TransactionAction.SEND, result?.action)
        assertEquals(BigDecimal("12.50"), result?.amount)
        assertEquals("John", result?.receiverName)
        assertEquals("CHF", result?.currency)
    }

    @Test
    fun parsesEuroCurrency() {
        val result = parser.parse("send 50 euros to Maria for the dinner")

        assertEquals(BigDecimal("50"), result?.amount)
        assertEquals("Maria", result?.receiverName)
        assertEquals("EUR", result?.currency)
        assertEquals("for the dinner", result?.message)
    }

    @Test
    fun parsesDollarCurrency() {
        val result = parser.parse("pay 20 dollars to John")

        assertEquals(BigDecimal("20"), result?.amount)
        assertEquals("John", result?.receiverName)
        assertEquals("USD", result?.currency)
    }

    @Test
    fun defaultsToChfWhenNoCurrencySpoken() {
        val result = parser.parse("send 10 to Stefan")

        assertEquals("Stefan", result?.receiverName)
        assertEquals(DEFAULT_CURRENCY, result?.currency)
    }

    @Test
    fun parsesMultiWordReceiver() {
        val result = parser.parse("send 40 to John Smith for dinner")

        assertEquals("John Smith", result?.receiverName)
    }

    @Test
    fun parsesSpelledOutAmount() {
        val result = parser.parse("send ten to Stefan")

        assertEquals(BigDecimal(10), result?.amount)
        assertEquals("Stefan", result?.receiverName)
    }

    @Test
    fun parsesVerbAdjacentReceiverWithoutPreposition() {
        val result = parser.parse("pay Tamara 15 francs")

        assertEquals(TransactionAction.SEND, result?.action)
        assertEquals(BigDecimal("15"), result?.amount)
        assertEquals("Tamara", result?.receiverName)
        assertEquals("CHF", result?.currency)
    }

    @Test
    fun isCaseInsensitive() {
        val result = parser.parse("SEND 10 TO STEFAN")

        assertEquals(TransactionAction.SEND, result?.action)
        assertEquals("Stefan", result?.receiverName)
    }

    @Test
    fun returnsNullForNonCommand() {
        assertNull(parser.parse("what is the weather today"))
    }

    @Test
    fun returnsNullWhenAmountMissing() {
        assertNull(parser.parse("send money to Stefan"))
    }

    @Test
    fun returnsNullWhenReceiverMissing() {
        assertNull(parser.parse("send 10 francs"))
    }

    @Test
    fun returnsNullForEmptyTranscript() {
        assertNull(parser.parse(""))
        assertNull(parser.parse("   "))
    }

    // --- Slide E14: before → after hardening ---

    @Test
    fun e14_parsesDemoSentence() {
        assertEquals(
            Transaction(TransactionAction.REQUEST, BigDecimal("10"), "EUR", "Marija", "for the taxi bill"),
            parser.parse("Request 10 euros from Marija for the taxi bill.")
        )
    }

    @Test
    fun e14_parsesCompoundNumberWords() {
        assertAmount("25", "Send twenty five francs to Stefan")
    }

    @Test
    fun e14_parsesHundred() {
        assertAmount("100", "Send one hundred francs to Stefan")
    }

    @Test
    fun e14_parsesThousandsSeparator() {
        assertAmount("1000", "Send 1,000 francs to Stefan")
    }

    @Test
    fun e14_skipsFillerWordBeforeReceiver() {
        val result = parser.parse("Pay back Tamara 15 francs")

        assertEquals(TransactionAction.SEND, result?.action)
        assertEquals(BigDecimal("15"), result?.amount)
        assertEquals("Tamara", result?.receiverName)
        assertEquals("CHF", result?.currency)
    }

    @Test
    fun e14_returnsNullForGermanCommand() {
        assertNull(parser.parse("Schick Stefan 10 Franken für die Pizza"))
    }

    @Test
    fun e14_returnsNullWithoutActionKeyword() {
        assertNull(parser.parse("I owe Stefan 10 francs for the pizza"))
    }

    // --- R1: compound number words up to 9,999 ---

    @Test
    fun r1_parsesHyphenatedNumberWords() {
        assertAmount("25", "Send twenty-five francs to Stefan")
    }

    @Test
    fun r1_parsesHundredAndTens() {
        assertAmount("120", "Send one hundred twenty francs to Stefan")
        assertAmount("120", "Send one hundred and twenty francs to Stefan")
    }

    @Test
    fun r1_parsesThousandsInWords() {
        assertAmount("2500", "Send two thousand five hundred francs to Stefan")
        assertAmount("1000", "Send a thousand francs to Stefan")
    }

    @Test
    fun r1_returnsNullForMalformedNumberWords() {
        assertNull(parser.parse("Send twenty twenty francs to Stefan"))
        assertNull(parser.parse("Send five six francs to Stefan"))
    }

    @Test
    fun r1_returnsNullAboveNineThousandNineHundredNinetyNine() {
        assertNull(parser.parse("Send ten thousand francs to Stefan"))
    }

    // --- R2: thousands separators vs decimals ---

    @Test
    fun r2_parsesApostropheAndSpaceThousandsSeparators() {
        assertAmount("1000", "Send 1'000 francs to Stefan")
        assertAmount("1000", "Send 1 000 francs to Stefan")
    }

    @Test
    fun r2_keepsDecimals() {
        assertAmount("2.80", "Send 2,80 francs to Stefan")
        assertAmount("12.50", "Send 12.50 francs to Stefan")
    }

    @Test
    fun r2_returnsNullForThreeDecimalDigits() {
        assertNull(parser.parse("Send 12.500 francs to Stefan"))
    }

    // --- R3: currency symbols attached to the amount ---

    @Test
    fun r3_parsesEuroSymbolBeforeAmount() {
        assertAmountAndCurrency("5", "EUR", "Send €5 to Ana")
    }

    @Test
    fun r3_parsesDollarSymbolBeforeAmount() {
        assertAmountAndCurrency("20", "USD", "Send $20 to Ana")
    }

    @Test
    fun r3_parsesCodeGluedToAmount() {
        assertAmountAndCurrency("12", "CHF", "Send CHF12 to Ana")
    }

    @Test
    fun r3_parsesEuroSymbolAfterAmount() {
        assertAmountAndCurrency("5", "EUR", "Send 5€ to Ana")
    }

    // --- R4: filler words ---

    @Test
    fun r4_neverUsesMeAsReceiver() {
        assertNull(parser.parse("Send me 10 francs"))
    }

    @Test
    fun r4_doesNotTreatNumberWordsAsName() {
        assertEquals("Tamara", parser.parse("pay Tamara fifteen francs")?.receiverName)
    }

    // --- R5: ambiguous amounts ---

    @Test
    fun r5_returnsNullForTwoDifferentAmounts() {
        assertNull(parser.parse("Send 10 or 20 francs to Stefan"))
    }

    @Test
    fun r5_failsClosedWhenReasonStartsWithANumber() {
        val result = parser.parse("Send 10 francs to Ana for 2 coffees")

        // "for 2 coffees" starts with a number, so it is read as an amount intro → two amounts.
        assertNull(result)
    }

    @Test
    fun r5_ignoresNumbersAfterTheReasonClauseStarts() {
        val result = parser.parse("Send 10 francs to Ana for the 2 coffees")

        assertEquals(BigDecimal("10"), result?.amount)
        assertEquals("for the 2 coffees", result?.message)
    }

    // --- R6: default currency ---

    @Test
    fun r6_defaultsToChf() {
        assertAmountAndCurrency("15", "CHF", "Pay Tamara 15")
    }

    // --- Regressions ---

    @Test
    fun regression_keepsCodeBeforeAmountWithDecimals() {
        assertEquals(
            Transaction(TransactionAction.SEND, BigDecimal("12.50"), "CHF", "Ana", "for lunch"),
            parser.parse("Send CHF 12.50 to Ana for lunch")
        )
    }

    @Test
    fun regression_recognizesEuroSymbol() {
        assertAmountAndCurrency("5", "EUR", "Send €5 to Ana")
    }

    private fun assertAmount(expected: String, transcript: String) {
        val result = parser.parse(transcript)
        assertEquals(transcript, 0, BigDecimal(expected).compareTo(result?.amount ?: BigDecimal("-1")))
    }

    private fun assertAmountAndCurrency(amount: String, currency: String, transcript: String) {
        assertAmount(amount, transcript)
        assertEquals(transcript, currency, parser.parse(transcript)?.currency)
    }
}
