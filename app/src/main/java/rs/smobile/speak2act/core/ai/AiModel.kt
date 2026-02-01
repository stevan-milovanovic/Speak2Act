package rs.smobile.speak2act.core.ai

import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.generationConfig

private const val MODEL_NAME = "gemini-2.5-flash-lite"
private val DEFAULT_GENERATION_CONFIG = generationConfig {
    temperature = 0.2f
    responseModalities = listOf(ResponseModality.TEXT)
}
private val DEFAULT_SAFETY_SETTINGS = listOf(
    SafetySetting(
        HarmCategory.HATE_SPEECH,
        HarmBlockThreshold.MEDIUM_AND_ABOVE
    ),
    SafetySetting(
        HarmCategory.HARASSMENT,
        HarmBlockThreshold.MEDIUM_AND_ABOVE
    )
)

data class AiModel(
    val name: String = MODEL_NAME,
    val generationConfig: GenerationConfig = DEFAULT_GENERATION_CONFIG,
    val safetySettings: List<SafetySetting> = DEFAULT_SAFETY_SETTINGS,
    val systemInstruction: String,
    val tools: List<Tool>? = emptyList()
)