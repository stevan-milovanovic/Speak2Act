package rs.smobile.speak2act.ui.voicerecorder.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.content
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
import rs.smobile.speak2act.ai.AiModels
import rs.smobile.speak2act.ai.AiModels.ACTION_PARAM
import rs.smobile.speak2act.ai.AiModels.AMOUNT_PARAM
import rs.smobile.speak2act.ai.AiModels.CURRENCY_PARAM
import rs.smobile.speak2act.ai.AiModels.DESCRIPTION_PARAM
import rs.smobile.speak2act.ai.AiModels.EXECUTE_TRANSACTION_FUNCTION_NAME
import rs.smobile.speak2act.ai.AiModels.PERSON_PARAM
import rs.smobile.speak2act.audio.domain.AudioRecorder
import rs.smobile.speak2act.domain.Transaction
import rs.smobile.speak2act.ui.voicerecorder.VoiceRecorderUiState
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder
) : ViewModel() {

    private companion object {
        private const val TAG = "RecorderViewModel"
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
        with(AiModels.SpeechToTransaction) {
            val generativeModel = genAI.generativeModel(
                modelName = name,
                generationConfig = generationConfig,
                safetySettings = safetySettings,
                systemInstruction = content { systemInstruction },
                tools = tools
            )
            chat = generativeModel.startChat()
        }
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

    fun clearData() {
        _uiState.value = VoiceRecorderUiState.Initial
        _amplitudes.value = emptyList()
        _isRecording.value = false
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
                            _uiState.value = VoiceRecorderUiState.Success(
                                transaction = functionCall.extractTransaction()
                            )
                        }

                        else -> Log.e(TAG, "Model recognized unknown function $name")
                    }
                }
            }
        }
    }

}

private fun FunctionCallPart.extractTransaction(): Transaction? {
    val action = extract(ACTION_PARAM) ?: return null
    val amount = extract(AMOUNT_PARAM)?.toDouble() ?: return null
    val person = extract(PERSON_PARAM) ?: return null
    val currency = extract(CURRENCY_PARAM) ?: return null
    val description = extract(DESCRIPTION_PARAM) ?: return null
    return Transaction(action, amount, person, currency, description)
}

private fun FunctionCallPart.extract(param: String): String? = args[param]?.jsonPrimitive?.content