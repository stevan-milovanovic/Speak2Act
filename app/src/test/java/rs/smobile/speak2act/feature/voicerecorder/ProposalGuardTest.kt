package rs.smobile.speak2act.feature.voicerecorder

import org.junit.Assert.assertEquals
import org.junit.Test
import rs.smobile.speak2act.feature.voicerecorder.domain.GuardResult
import rs.smobile.speak2act.feature.voicerecorder.domain.ProposalGuard
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction
import rs.smobile.speak2act.feature.voicerecorder.domain.TransactionAction
import rs.smobile.speak2act.feature.voicerecorder.domain.VoiceTransactionParser
import java.math.BigDecimal

class ProposalGuardTest {

    private val guard = ProposalGuard(VoiceTransactionParser())

    private fun proposal(
        amount: String = "10",
        currency: String = "CHF",
        person: String = "Stefan",
        message: String? = null,
        action: TransactionAction = TransactionAction.SEND,
    ) = Transaction(action, BigDecimal(amount), currency, person, message)

    private fun assertRejected(reason: String, proposal: Transaction, transcript: String) {
        assertEquals(transcript, GuardResult.Rejected(reason), guard.check(proposal, transcript))
    }

    private fun assertAccepted(proposal: Transaction, transcript: String) {
        assertEquals(transcript, GuardResult.Accepted(proposal), guard.check(proposal, transcript))
    }

    @Test
    fun acceptsAGroundedProposal() {
        assertAccepted(proposal(message = "pizza"), "I owe Stefan ten francs for the pizza.")
    }

    @Test
    fun acceptsNumberWords_andEqualDecimals() {
        assertAccepted(proposal(amount = "25", person = "Tamara"), "Pay Tamara twenty-five francs")
        assertAccepted(proposal(amount = "12.5", person = "Ana"), "Pay Ana 12.50 francs")
        assertAccepted(proposal(amount = "12.50", person = "Ana"), "Pay Ana 12.5 francs")
    }

    @Test
    fun rejectsAnInventedAmount() {
        assertRejected("amount_not_spoken", proposal(amount = "100"), "I owe Stefan ten francs for the pizza.")
        assertRejected("amount_not_spoken", proposal(), "Send some money to Stefan.")
    }

    @Test
    fun rejectsTwoAmounts_oneTransactionPerCommand() {
        val transcript = "Transfer 100 francs to Stefan and 50 to Maria."
        assertRejected("amount_ambiguous", proposal(amount = "100"), transcript)
        assertRejected("amount_ambiguous", proposal(amount = "50", person = "Maria"), transcript)
        assertRejected("amount_ambiguous", proposal(person = "Ana"), "I owe Ana 10 francs and Bo 10 francs.")
    }

    @Test
    fun rejectsAnUnsupportedCurrency() {
        assertRejected("currency_unsupported", proposal(amount = "35", person = "David"), "Request 35 yen from David.")
    }

    @Test
    fun rejectsTwoCurrencies() {
        assertRejected(
            "currency_ambiguous",
            proposal(currency = "EUR", person = "Ana"),
            "Send 10 euros or francs to Ana."
        )
    }

    @Test
    fun rejectsACurrencyMismatch() {
        assertRejected(
            "currency_mismatch",
            proposal(currency = "CHF", person = "Maria", action = TransactionAction.REQUEST),
            "Request 10 euros from Maria."
        )
    }

    @Test
    fun noCurrencySaid_acceptsOnlyTheDefault() {
        val transcript = "I owe Stefan 10 for the pizza."
        assertAccepted(proposal(currency = "CHF"), transcript)
        assertRejected("currency_mismatch", proposal(currency = "EUR"), transcript)
        assertRejected("currency_mismatch", proposal(currency = "JPY"), transcript)
    }

    @Test
    fun rejectsAnInventedPerson() {
        val transcript = "I owe Stefan ten francs for the pizza."
        assertRejected("person_not_spoken", proposal(person = "Maria"), transcript)
        assertRejected("person_not_spoken", proposal(person = "Stefan Smith"), transcript)
        assertRejected("person_not_spoken", proposal(person = "-"), transcript)
    }

    @Test
    fun ignoresAccents_andPossessives() {
        assertAccepted(proposal(amount = "12.5", person = "Chloé"), "Pay Chloe 12.50")
        assertAccepted(proposal(amount = "20", currency = "EUR", person = "Anna Weber"), "Give Anna Weber's share, 20 euros")
        assertAccepted(proposal(person = "Milovanovic"), "Send 10 francs to Milovanović")
    }

    @Test
    fun dropsAnUngroundedReason_butKeepsTheProposal() {
        val result = guard.check(proposal(message = "dinner"), "I owe Stefan ten francs for the pizza.")

        assertEquals(GuardResult.Accepted(proposal(message = null)), result)
    }
}
