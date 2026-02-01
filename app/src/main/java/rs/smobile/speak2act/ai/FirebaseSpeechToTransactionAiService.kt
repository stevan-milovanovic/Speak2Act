package rs.smobile.speak2act.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import rs.smobile.speak2act.domain.Transaction
import javax.inject.Inject

/**
 * Firebase-based implementation of [SpeechToTransactionAiService].
 * Encapsulates creation of the generative model and parsing of responses.
 */
class FirebaseSpeechToTransactionAiService @Inject constructor() : SpeechToTransactionAiService {

    private val contentBuilder = Content.Builder().apply {
        part(TextPart("Analyze the audio and extract transaction instructions"))
    }

    override suspend fun extractTransaction(audioBytes: ByteArray): Result<Transaction?> =
        withContext(Dispatchers.IO) {
            try {
                val genAI = Firebase.ai(
                    backend = GenerativeBackend.googleAI()
                )

                val model = with(AiModels.SpeechToTransaction) {
                    genAI.generativeModel(
                        modelName = name,
                        generationConfig = generationConfig,
                        safetySettings = safetySettings,
                        systemInstruction = content { systemInstruction },
                        tools = tools
                    )
                }

                val chat = model.startChat()
                contentBuilder.inlineData(audioBytes, "audio/mp4")
                val prompt = contentBuilder.build()
                val response = chat.sendMessage(prompt)

                if (response.functionCalls.isEmpty()) {
                    val candidate = response.candidates.firstOrNull()
                    val instructions = candidate?.content?.parts?.filterIsInstance<TextPart>()
                        ?.map { it.text }
                        ?: listOf("No textual candidate returned")
                    return@withContext Result.failure(Exception(instructions.joinToString()))
                }

                response.functionCalls.forEach { functionCall ->
                    if (functionCall.name == AiModels.EXECUTE_TRANSACTION_FUNCTION_NAME) {
                        return@withContext Result.success(functionCall.extractTransaction())
                    }
                }

                Result.failure(Exception("No transaction function call returned"))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

}

private fun FunctionCallPart.extractTransaction(): Transaction? {
    val action = extract(AiModels.ACTION_PARAM) ?: return null
    val amount = extract(AiModels.AMOUNT_PARAM)?.toDoubleOrNull() ?: return null
    val person = extract(AiModels.PERSON_PARAM) ?: return null
    val currency = extract(AiModels.CURRENCY_PARAM) ?: return null
    val description = extract(AiModels.DESCRIPTION_PARAM) ?: return null
    return Transaction(action, amount, currency, person, description)
}

private fun FunctionCallPart.extract(param: String): String? = args[param]?.jsonPrimitive?.content
