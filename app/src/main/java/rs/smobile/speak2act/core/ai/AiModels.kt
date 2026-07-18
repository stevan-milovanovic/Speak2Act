package rs.smobile.speak2act.core.ai

import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig

object AiModels {

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