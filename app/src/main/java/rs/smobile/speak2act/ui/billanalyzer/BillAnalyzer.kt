package rs.smobile.speak2act.ui.billanalyzer

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import rs.smobile.speak2act.ai.AiModels
import rs.smobile.speak2act.bill.Bill
import rs.smobile.speak2act.bill.BillItem
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BillAnalyzer @Inject constructor() {

    private companion object {
        private const val TAG = "BillAnalyzerViewModel"
        private const val ITEMS_KEY = "items"
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val generativeModel: GenerativeModel

    private val jsonParser = Json {
        ignoreUnknownKeys = true    //Prevents crashes if Gemini adds extra fields
        coerceInputValues = true    //Helps if types are slightly off
        isLenient = true            //Handles slightly malformed JSON
        explicitNulls = false
        decodeEnumsCaseInsensitive = true
    }

    init {
        val genAI = Firebase.ai(backend = GenerativeBackend.googleAI())
        with(AiModels.BillAnalyzer) {
            generativeModel = genAI.generativeModel(
                modelName = name,
                generationConfig = generationConfig,
                safetySettings = safetySettings,
                systemInstruction = content { systemInstruction },
                tools = tools
            )
        }
    }

    fun ocrOnDevice(image: InputImage): Flow<Bill?> = flow {
        val ocrResult = recognizer.process(image).await()
        val bill = processRawResult(ocrResult.text)
        emit(bill)
    }

    private suspend fun processRawResult(rawOcrText: String): Bill? {
        Log.d(TAG, "rawOcrText: $rawOcrText")
        val response = generativeModel.generateContent(rawOcrText)
        val jsonString = response.text ?: return null
        Log.d(TAG, "Generated JSON: $jsonString")
        return heuristicParse(jsonString)
    }

    private fun heuristicParse(jsonString: String): Bill {
        val jsonElement = jsonParser.parseToJsonElement(jsonString)
        val itemsElement = jsonElement.findKeyRecursively(ITEMS_KEY)
        val items = itemsElement?.let {
            jsonParser.decodeFromJsonElement<List<BillItem>>(it)
        } ?: emptyList()
        return Bill(items = items)
    }

    private fun JsonElement.findKeyRecursively(targetKey: String): JsonElement? = when (this) {
        is JsonArray -> {
            firstNotNullOfOrNull { it.findKeyRecursively(targetKey) }
        }

        is JsonObject -> {
            this[targetKey] ?: values.firstNotNullOfOrNull { it.findKeyRecursively(targetKey) }
        }

        else -> null
    }

}