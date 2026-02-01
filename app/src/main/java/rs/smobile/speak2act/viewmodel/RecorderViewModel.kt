package rs.smobile.speak2act.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rs.smobile.speak2act.ai.SpeechToTransactionAiService
import rs.smobile.speak2act.audio.domain.AudioRecorder
import rs.smobile.speak2act.ui.voicerecorder.VoiceRecorderUiState
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val speechToTransactionAiService: SpeechToTransactionAiService
) : ViewModel() {

    private companion object {
        private const val TAG = "RecorderViewModel"
    }

    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes = _amplitudes.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private var recordingJob: Job? = null

    private val _uiState = MutableStateFlow<VoiceRecorderUiState>(VoiceRecorderUiState.Initial)
    val uiState: StateFlow<VoiceRecorderUiState> = _uiState

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
        viewModelScope.launch {
            val result = try {
                speechToTransactionAiService.extractTransaction(fileInBytes)
            } catch (t: Throwable) {
                Result.failure(t)
            }

            result.fold(
                onSuccess = { transaction ->
                    if (transaction != null) {
                        _uiState.value = VoiceRecorderUiState.Success(transaction = transaction)
                    } else {
                        _uiState.value = VoiceRecorderUiState.Error("No transaction extracted")
                    }
                },
                onFailure = { t ->
                    Log.e(TAG, "AI extraction failed", t)
                    _uiState.value = VoiceRecorderUiState.Error(t.message ?: "Unknown AI error")
                }
            )
        }
    }

}