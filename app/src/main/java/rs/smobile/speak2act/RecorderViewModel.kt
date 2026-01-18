package rs.smobile.speak2act

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RecorderViewModel() : ViewModel() {

    private var contentBuilder = Content.Builder()
    private var chat: Chat

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result

    init {
        val genAI = Firebase.ai(
            backend = GenerativeBackend.googleAI()
        )
        val generativeModel = genAI.generativeModel(
            modelName = "gemini-3-flash-preview",
            generationConfig = generationConfig {
                temperature = 0.2f
                responseModalities = listOf(ResponseModality.TEXT)
            },
            safetySettings = listOf(
                SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.MEDIUM_AND_ABOVE),
                SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.MEDIUM_AND_ABOVE)
            ),
            systemInstruction = content {
                "You are a banking app. The user records an audio message in which he's saying what is the action, " +
                        "e.g. pay, split, request the money. It defines the value in swiss francs and he specifies to whom he wants to " +
                        "send the money, or to split the money with, or to request the money from. Optionally he can define the reason " +
                        "for the transaction, like it's for a dinner or travel expenses. " +
                        "Extract in bullet points: 1. action, 2. amount, 3. person (contact) and 4. context."
            }
        )
        chat = generativeModel.startChat()
        contentBuilder.part(
            TextPart("Analyze the audio and extract transaction instructions")
        )
    }

    fun sendPrompt(
        fileInBytes: ByteArray
    ) {
        _result.value = ""
        contentBuilder.inlineData(fileInBytes, "audio/mp4")
        val prompt = contentBuilder
            .build()

        viewModelScope.launch {
            val response = chat.sendMessage(prompt)
            val candidate = response.candidates.first()
            val instructions = candidate.content.parts.filter { it is TextPart }
            val textParts = instructions.map { (it as TextPart).text }
            _result.value = textParts.joinToString()
        }
    }

}