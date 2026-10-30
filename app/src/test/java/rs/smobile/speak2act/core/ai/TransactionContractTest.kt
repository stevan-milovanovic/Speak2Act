package rs.smobile.speak2act.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TransactionContractTest {

    private companion object {
        /** The declaration the fine-tuned model was trained on (plan §8.2). */
        private const val EXPECTED_TOOL_JSON =
            """{"description":"Prepares a money transaction for the user to confirm.","name":"executeTransaction","parameters":{"properties":{"action":{"description":"Type of the transaction.","enum":["pay","request","split"],"type":"string"},"amount":{"description":"Amount of the transaction.","type":"number"},"currency":{"description":"Currency of the transaction, e.g. CHF or EUR.","type":"string"},"description":{"description":"The reason for the transaction.","type":"string"},"person":{"description":"Contact to whom the transaction is addressed.","type":"string"}},"required":["action","amount","currency","person"],"type":"object"}}"""

        // Unit tests run with the app/ module as working directory.
        private val NOTEBOOK_TOOL_FILE = File("../ml/functiongemma/execute_transaction.json")
    }

    private val tool = Json.parseToJsonElement(TransactionContract.toolJson).jsonObject

    @Test
    fun toolJson_isTheDeclarationTheModelWasTrainedOn() {
        assertEquals(EXPECTED_TOOL_JSON, TransactionContract.toolJson)
    }

    @Test
    fun toolJson_declaresFiveParameters_threeActions_andFourRequired() {
        val parameters = tool.getValue("parameters").jsonObject
        val properties = parameters.getValue("properties").jsonObject

        assertEquals(TransactionContract.FUNCTION_NAME, tool.getValue("name").jsonPrimitive.content)
        assertEquals(setOf("action", "amount", "currency", "description", "person"), properties.keys)
        assertEquals(
            listOf("pay", "request", "split"),
            properties.getValue("action").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content }
        )
        assertEquals(
            listOf("action", "amount", "currency", "person"),
            parameters.getValue("required").jsonArray.map { it.jsonPrimitive.content }
        )
    }

    @Test
    fun toolJson_keysAreAlphabeticalAtEveryLevel() {
        assertAlphabetical(tool)
    }

    @Test
    fun toolJson_equalsTheNotebooksDeclaration() {
        assertTrue("Missing ${NOTEBOOK_TOOL_FILE.absolutePath}", NOTEBOOK_TOOL_FILE.isFile)
        assertEquals(Json.parseToJsonElement(NOTEBOOK_TOOL_FILE.readText()), tool)
    }

    private fun assertAlphabetical(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                assertEquals(element.keys.sorted(), element.keys.toList())
                element.values.forEach(::assertAlphabetical)
            }

            is JsonArray -> element.forEach(::assertAlphabetical)
            else -> Unit
        }
    }
}
