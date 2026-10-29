package rs.smobile.speak2act.feature.voicerecorder

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.smobile.speak2act.feature.voicerecorder.data.toTransactionOrNull
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction
import rs.smobile.speak2act.feature.voicerecorder.domain.TransactionAction
import java.math.BigDecimal

class TransactionMapperTest {

    private fun args(
        action: JsonElement? = JsonPrimitive("pay"),
        amount: JsonElement? = JsonPrimitive(25.0),
        currency: JsonElement? = JsonPrimitive("CHF"),
        person: JsonElement? = JsonPrimitive("Luka"),
        description: JsonElement? = null,
    ): Map<String, JsonElement> = buildMap {
        action?.let { put("action", it) }
        amount?.let { put("amount", it) }
        currency?.let { put("currency", it) }
        person?.let { put("person", it) }
        description?.let { put("description", it) }
    }

    @Test
    fun mapsPayRequestAndSplit() {
        assertEquals(TransactionAction.SEND, args(action = JsonPrimitive("pay")).toTransactionOrNull()?.action)
        assertEquals(TransactionAction.REQUEST, args(action = JsonPrimitive("Request")).toTransactionOrNull()?.action)
        assertEquals(TransactionAction.SPLIT, args(action = JsonPrimitive("split")).toTransactionOrNull()?.action)
    }

    @Test
    fun unknownActionMapsToNull() {
        assertNull(args(action = JsonPrimitive("refund")).toTransactionOrNull())
    }

    @Test
    fun readsAmountAsJsonNumberAndAsString() {
        assertEquals(0, BigDecimal("25").compareTo(args(amount = JsonPrimitive(25.0)).toTransactionOrNull()!!.amount))
        assertEquals(0, BigDecimal("12.5").compareTo(args(amount = JsonPrimitive("12.50")).toTransactionOrNull()!!.amount))
    }

    @Test
    fun nonPositiveAmountMapsToNull() {
        assertNull(args(amount = JsonPrimitive(0)).toTransactionOrNull())
        assertNull(args(amount = JsonPrimitive(-5)).toTransactionOrNull())
    }

    @Test
    fun missingPersonMapsToNull() {
        assertNull(args(person = null).toTransactionOrNull())
        assertNull(args(person = JsonPrimitive(" ")).toTransactionOrNull())
    }

    @Test
    fun normalizesCurrencyWords() {
        assertEquals("EUR", args(currency = JsonPrimitive("euros")).toTransactionOrNull()?.currency)
        assertEquals("USD", args(currency = JsonPrimitive("usd")).toTransactionOrNull()?.currency)
    }

    @Test
    fun missingCurrencyDefaultsToChf() {
        assertEquals("CHF", args(currency = null).toTransactionOrNull()?.currency)
        assertEquals("CHF", args(currency = JsonNull).toTransactionOrNull()?.currency)
    }

    @Test
    fun jsonNullDescriptionMapsToNullMessage() {
        assertNull(args(description = JsonNull).toTransactionOrNull()?.message)
    }

    @Test
    fun mapsAllFields() {
        assertEquals(
            Transaction(TransactionAction.SPLIT, BigDecimal("60"), "CHF", "Ana", "dinner"),
            args(
                action = JsonPrimitive("split"),
                amount = JsonPrimitive("60"),
                person = JsonPrimitive("Ana"),
                description = JsonPrimitive("dinner"),
            ).toTransactionOrNull()
        )
    }
}
