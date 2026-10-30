package rs.smobile.speak2act.core.ai

import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.generationConfig

object AiModels {

    // Aliases of the shared contract, so the mapper and the Gemini service keep their imports.
    const val EXECUTE_TRANSACTION_FUNCTION_NAME = TransactionContract.FUNCTION_NAME
    const val ACTION_PARAM = TransactionContract.ACTION_PARAM
    const val AMOUNT_PARAM = TransactionContract.AMOUNT_PARAM
    const val CURRENCY_PARAM = TransactionContract.CURRENCY_PARAM
    const val PERSON_PARAM = TransactionContract.PERSON_PARAM
    const val DESCRIPTION_PARAM = TransactionContract.DESCRIPTION_PARAM

    val SpeechToTransaction = AiModel(
        systemInstruction = """
            You are the voice assistant of a mobile payments app.
            The user records a short spoken instruction to pay, request or split money with one of their contacts.
            Call the executeTransaction function with:
            - action: pay, request or split
            - amount: the numeric amount
            - currency: the currency the user mentions; if none is mentioned, use CHF
            - person: the contact name exactly as spoken
            - description: the reason for the transaction, only if mentioned
            If the action, the amount or the person is missing or unclear, do not call the function.
            Instead, reply with one short clarifying question.
        """.trimIndent(),
        tools = listOf(
            Tool.functionDeclarations(
                listOf(
                    FunctionDeclaration(
                        name = EXECUTE_TRANSACTION_FUNCTION_NAME,
                        description = TransactionContract.FUNCTION_DESCRIPTION,
                        parameters = mapOf(
                            ACTION_PARAM to Schema.enumeration(
                                TransactionContract.ACTIONS, TransactionContract.ACTION_DESCRIPTION
                            ),
                            AMOUNT_PARAM to Schema.double(TransactionContract.AMOUNT_DESCRIPTION),
                            CURRENCY_PARAM to Schema.string(TransactionContract.CURRENCY_DESCRIPTION),
                            PERSON_PARAM to Schema.string(TransactionContract.PERSON_DESCRIPTION),
                            DESCRIPTION_PARAM to Schema.string(TransactionContract.REASON_DESCRIPTION),
                        ),
                        optionalParameters = listOf(DESCRIPTION_PARAM),
                    )
                )
            )
        )
    )

    /**
     * Cloud fallback of the hybrid voice pipeline: receives only the on-device transcript (text).
     * Same function contract as [SpeechToTransaction].
     */
    val TranscriptToTransaction = AiModel(
        systemInstruction = """
            You are the voice assistant of a mobile payments app.
            The user spoke a short instruction to pay, request or split money with one of their contacts.
            You receive the text as it was transcribed on the device. It may contain recognition errors
            (misspelled names, numbers written as words, missing punctuation).
            Call the executeTransaction function with:
            - action: pay, request or split
            - amount: the numeric amount
            - currency: the currency the user mentions; if none is mentioned, use CHF
            - person: the contact name as transcribed
            - description: the reason for the transaction, only if mentioned
            If the action, the amount or the person is missing or unclear, do not call the function.
            Instead, reply with one short clarifying question.
        """.trimIndent(),
        tools = SpeechToTransaction.tools
    )

    // Declared before BillAnalyzer: object properties initialize top to bottom, so a schema
    // declared below the model would still be null when the model's generationConfig is built.
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

}
