package rs.smobile.speak2act.feature.voicerecorder

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.smobile.speak2act.feature.voicerecorder.domain.VoiceTransactionAction
import rs.smobile.speak2act.feature.voicerecorder.domain.VoiceTransactionParser

class VoiceTransactionParserTest {

    private val parser = VoiceTransactionParser()

    @Test
    fun parsesSendCommandWithReasonClause() {
        val result = parser.parse("Send 10 francs to Stefan for the pizza")

        assertEquals(VoiceTransactionAction.SEND, result?.action)
        assertEquals(BigDecimal("10"), result?.amount)
        assertEquals("Stefan", result?.receiverName)
        assertEquals("CHF", result?.currency)
        assertEquals("for the pizza", result?.message)
    }

    @Test
    fun parsesRequestWithAmountIntroAndReasonClause() {
        val result = parser.parse("Ask Tamara for 2.80 francs for the train ticket")

        assertEquals(VoiceTransactionAction.REQUEST, result?.action)
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

        assertEquals(VoiceTransactionAction.SEND, result?.action)
        assertEquals(BigDecimal("10"), result?.amount)
        assertEquals("Stefan", result?.receiverName)
        assertEquals("CHF", result?.currency)
    }

    @Test
    fun parsesRequestCommandWithFrom() {
        val result = parser.parse("Request 25 from Maria")

        assertEquals(VoiceTransactionAction.REQUEST, result?.action)
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

        assertEquals(VoiceTransactionAction.SEND, result?.action)
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
    fun hasNullCurrencyWhenNoneSpoken() {
        val result = parser.parse("send 10 to Stefan")

        assertEquals("Stefan", result?.receiverName)
        assertNull(result?.currency)
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

        assertEquals(VoiceTransactionAction.SEND, result?.action)
        assertEquals(BigDecimal("15"), result?.amount)
        assertEquals("Tamara", result?.receiverName)
        assertEquals("CHF", result?.currency)
    }

    @Test
    fun isCaseInsensitive() {
        val result = parser.parse("SEND 10 TO STEFAN")

        assertEquals(VoiceTransactionAction.SEND, result?.action)
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
}
