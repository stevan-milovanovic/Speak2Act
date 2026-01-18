package rs.smobile.speak2act.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import rs.smobile.speak2act.audio.domain.AudioRecorder
import rs.smobile.speak2act.ui.VoiceRecorderUiState
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder
) : ViewModel() {

    private companion object {
        private const val TAG = "RecorderViewModel"
        private const val MODEL_NAME = "gemini-2.5-flash"
        private const val EXECUTE_TRANSACTION_FUNCTION_NAME = "executeTransaction"
        private const val ACTION_PARAM = "action"
        private const val AMOUNT_PARAM = "amount"
        private const val CURRENCY_PARAM = "currency"
        private const val PERSON_PARAM = "person"
        private const val DESCRIPTION_PARAM = "description"
    }

    private var contentBuilder = Content.Builder()
    private var chat: Chat

    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes = _amplitudes.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private var recordingJob: Job? = null

    private val _uiState = MutableStateFlow<VoiceRecorderUiState>(VoiceRecorderUiState.Initial)
    val uiState: StateFlow<VoiceRecorderUiState> = _uiState

    init {
        val genAI = Firebase.ai(
            backend = GenerativeBackend.googleAI()
        )
        val generativeModel = genAI.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.2f
                responseModalities = listOf(ResponseModality.TEXT)
            },
            safetySettings = listOf(
                SafetySetting(
                    HarmCategory.HATE_SPEECH,
                    HarmBlockThreshold.MEDIUM_AND_ABOVE
                ),
                SafetySetting(
                    HarmCategory.HARASSMENT,
                    HarmBlockThreshold.MEDIUM_AND_ABOVE
                )
            ),
            systemInstruction = content {
                "You are a banking app. The user records an audio message in which he's saying what is the action, " +
                        "e.g. pay, split, request the money. It defines the value in swiss francs and he specifies to whom he wants to " +
                        "send the money, or to split the money with, or to request the money from. Optionally he can define the reason " +
                        "for the transaction, like it's for a dinner or travel expenses. " +
                        "Extract in bullet points: 1.action, 2.amount, 3.currency, 4.person(contact) and 5.description(reason)."
            },
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
        chat = generativeModel.startChat()
        contentBuilder.part(
            TextPart("Analyze the audio and extract transaction instructions")
        )
    }

    fun startRecording() {
        _uiState.value = VoiceRecorderUiState.Initial
        audioRecorder.start()
        _isRecording.value = true

        recordingJob = viewModelScope.launch {
            while (isActive) {
                _amplitudes.update { it + audioRecorder.getAmplitude() }
                delay(100)
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        _isRecording.value = false
        val file = audioRecorder.stop()
        sendPrompt(file.readBytes())
    }

    override fun onCleared() {
        audioRecorder.stop()
    }

    private fun sendPrompt(
        fileInBytes: ByteArray
    ) {
        _uiState.value = VoiceRecorderUiState.Loading
        contentBuilder.inlineData(fileInBytes, "audio/mp4")
        val prompt = contentBuilder
            .build()

        viewModelScope.launch {
            val response = chat.sendMessage(prompt)

            if (response.functionCalls.isEmpty()) {
                val candidate = response.candidates.first()
                val instructions = candidate.content.parts.filter { it is TextPart }
                val textParts = instructions.map { (it as TextPart).text }
                textParts.forEach { Log.e(TAG, "Text part: $it") }
                _uiState.value = VoiceRecorderUiState.Error(textParts.joinToString())
            } else {
                response.functionCalls.forEach { functionCall ->
                    when (val name = functionCall.name) {
                        EXECUTE_TRANSACTION_FUNCTION_NAME -> {
                            // Handle the call to fetchWeather()
                            val action = functionCall.extract(ACTION_PARAM)
                            val amount = functionCall.extract(AMOUNT_PARAM)?.toDouble()
                            val person = functionCall.extract(PERSON_PARAM)
                            val currency = functionCall.extract(CURRENCY_PARAM)
                            val description = functionCall.extract(DESCRIPTION_PARAM)

                            _uiState.value = VoiceRecorderUiState.Success(
                                action,
                                amount,
                                currency,
                                person,
                                description
                            )
                        }

                        else -> Log.e(TAG, "Model recognized unknown function $name")
                    }
                }
            }
        }
    }

}

private fun FunctionCallPart.extract(param: String): String? = args[param]?.jsonPrimitive?.content