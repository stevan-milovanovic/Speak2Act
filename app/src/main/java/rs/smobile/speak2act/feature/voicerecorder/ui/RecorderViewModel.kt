package rs.smobile.speak2act.feature.voicerecorder.ui

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
import rs.smobile.speak2act.feature.voicerecorder.data.WhisperAudioRecorder
import rs.smobile.speak2act.feature.voicerecorder.domain.AudioRecorder
import rs.smobile.speak2act.feature.voicerecorder.domain.SpeechToTransactionService
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class RecorderViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val speechToTransactionService: SpeechToTransactionService
) : ViewModel() {

    private companion object {
        private const val TAG = "RecorderViewModel"

        // Ignore accidental sub-100ms taps (16 kHz sample rate).
        private const val MIN_SAMPLES = WhisperAudioRecorder.SAMPLE_RATE_HZ / 10

        // Cap the live waveform history so it doesn't grow unbounded during long recordings.
        private const val MAX_AMPLITUDE_BARS = 96
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
                _amplitudes.update { (it + audioRecorder.getAmplitude()).takeLast(MAX_AMPLITUDE_BARS) }
                delay(100.milliseconds)
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        _isRecording.value = false
        val audio = audioRecorder.stop()
        if (audio.size < MIN_SAMPLES) {
            _uiState.value = VoiceRecorderUiState.Error("No recording available")
            return
        }
        transcribeAndParse(audio)
    }

    override fun onCleared() {
        try {
            audioRecorder.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "Error during audioRecorder.stop() onCleared", t)
        }
        speechToTransactionService.release()
    }

    fun clearData() {
        _uiState.value = VoiceRecorderUiState.Initial
        _amplitudes.value = emptyList()
        _isRecording.value = false
    }

    private fun transcribeAndParse(audio: FloatArray) {
        _uiState.value = VoiceRecorderUiState.Loading

        viewModelScope.launch {
            val result = runCatching {
                speechToTransactionService.parse(audio) { progress ->
                    _uiState.value = VoiceRecorderUiState.DownloadingModel(progress)
                }
            }

            result.fold(onSuccess = { transaction ->
                if (transaction != null) {
                    _uiState.value = VoiceRecorderUiState.Success(transaction = transaction)
                } else {
                    _uiState.value = VoiceRecorderUiState.Error("No transaction detected")
                }
            }, onFailure = { t ->
                Log.e(TAG, "On-device transcription failed", t)
                _uiState.value = VoiceRecorderUiState.Error(t.message ?: "Unknown transcription error")
            })
        }
    }

}
