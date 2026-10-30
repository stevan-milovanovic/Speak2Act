package rs.smobile.speak2act.feature.voicerecorder.llm

import com.google.ai.edge.litertlm.ToolCall
import com.google.gson.internal.LazilyParsedNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.smobile.speak2act.feature.voicerecorder.data.llm.toJsonArguments
import rs.smobile.speak2act.feature.voicerecorder.data.llm.toProposalOrNull
import rs.smobile.speak2act.feature.voicerecorder.data.toTransactionOrNull
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction
import rs.smobile.speak2act.feature.voicerecorder.domain.TransactionAction
import java.math.BigDecimal

/** LiteRT-LM tool-call arguments as the runtime returns them: Gson numbers, strings, nulls. */
class ToolArgumentsTest {

    private companion object {
        private val ARGUMENTS = mapOf(
            "action" to "pay",
            "amount" to LazilyParsedNumber("12.50"),
            "currency" to "CHF",
            "person" to "Stefan",
            "description" to "pizza",
        )
        private val TRANSACTION =
            Transaction(TransactionAction.SEND, BigDecimal("12.50"), "CHF", "Stefan", "pizza")
    }

    @Test
    fun gsonNumber_keepsTheAmountExactly() {
        val transaction = ARGUMENTS.toJsonArguments().toTransactionOrNull()

        assertEquals(TRANSACTION, transaction)
        assertEquals("12.50", transaction?.amount?.toPlainString())
    }

    @Test
    fun nullDescription_meansNoMessage() {
        val transaction = (ARGUMENTS + ("description" to null)).toJsonArguments().toTransactionOrNull()

        assertEquals(TRANSACTION.copy(message = null), transaction)
    }

    @Test
    fun unknownAction_isNull() {
        assertNull((ARGUMENTS + ("action" to "refund")).toJsonArguments().toTransactionOrNull())
    }

    @Test
    fun missingPerson_isNull() {
        assertNull((ARGUMENTS - "person").toJsonArguments().toTransactionOrNull())
    }

    @Test
    fun toProposalOrNull_takesExactlyOneExecuteTransactionCall() {
        val call = ToolCall("executeTransaction", ARGUMENTS)

        assertEquals(TRANSACTION, listOf(call).toProposalOrNull())
        assertNull(listOf(call, call.copy(arguments = ARGUMENTS + ("person" to "Maria"))).toProposalOrNull())
        assertNull(listOf(ToolCall("execute_transaction", ARGUMENTS)).toProposalOrNull())
        assertNull(emptyList<ToolCall>().toProposalOrNull())
    }
}
