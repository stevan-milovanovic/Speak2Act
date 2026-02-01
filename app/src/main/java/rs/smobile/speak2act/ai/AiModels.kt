package rs.smobile.speak2act.ai

import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.generationConfig

object AiModels {

    const val EXECUTE_TRANSACTION_FUNCTION_NAME = "executeTransaction"
    const val ACTION_PARAM = "action"
    const val AMOUNT_PARAM = "amount"
    const val CURRENCY_PARAM = "currency"
    const val PERSON_PARAM = "person"
    const val DESCRIPTION_PARAM = "description"

    val SpeechToTransaction = AiModel(
        systemInstruction = "You are a banking app. The user records an audio message in which he's saying what is the action, " +
                "e.g. pay, split, request the money. It defines the value in swiss francs and he specifies to whom he wants to " +
                "send the money, or to split the money with, or to request the money from. Optionally he can define the reason " +
                "for the transaction, like it's for a dinner or travel expenses. " +
                "Extract in bullet points: 1.action, 2.amount, 3.currency, 4.person(contact) and 5.description(reason).",
        tools = listOf(
            Tool.functionDeclarations(
                listOf(
                    FunctionDeclaration(
                        EXECUTE_TRANSACTION_FUNCTION_NAME,
                        "Get the transaction payload.",
                        mapOf(
                            ACTION_PARAM to Schema.string("Pay, split or request the money."),
                            AMOUNT_PARAM to Schema.string("Amount of the transaction. Should be double."),
                            CURRENCY_PARAM to Schema.string("Currency of the transaction."),
                            PERSON_PARAM to Schema.string("Contact to whom the transaction should be addressed to."),
                            DESCRIPTION_PARAM to Schema.string("The reason for the transaction."),
                        )
                    )
                )
            )
        )
    )

    val BillAnalyzer = AiModel(
        systemInstruction = "You are an expert data extraction assistant. " +
                "Your task is to extract line items from provided OCR text of a receipt. " +
                "IT IS STRICTLY REQUIRED to ALWAYS return a SINGLE JSON OBJECT as a ROOT. " +
                "with REQUIRED key 'items' (an array of objects). " +
                "Each object in 'items' array of objects must contain " +
                "'quantity' (integer), 'description' (string), and 'price' (number). " +
                "If a quantity is not found, default to 1. Do not include any conversational text, " +
                "markdown formatting, or explanations outside of the JSON.",
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = billSchema
        }
    )

    private val billItemSchema = Schema.obj(
        properties = mapOf(
            "quantity" to Schema.integer(description = "The number of units"),
            "description" to Schema.string(description = "The description of the item or dish"),
            "price" to Schema.double(description = "The price of the item")
        ),
        optionalProperties = listOf("quantity"),
        title = "items"
    )
    val billSchema = Schema.obj(
        properties = mapOf("items" to Schema.array(billItemSchema))
    )

}