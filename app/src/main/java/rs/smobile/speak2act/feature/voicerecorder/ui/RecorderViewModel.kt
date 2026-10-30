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
import rs.smobile.speak2act.core.config.DemoTalk
import rs.smobile.speak2act.core.privacy.CloudConsentStore
import rs.smobile.speak2act.feature.voicerecorder.data.WhisperAudioRecorder
import rs.smobile.speak2act.feature.voicerecorder.domain.AudioRecorder
import rs.smobile.speak2act.feature.voicerecorder.domain.OnDeviceModelStatus
import rs.smobile.speak2act.feature.voicerecorder.domain.OnDeviceTransactionModel
import rs.smobile.speak2act.feature.voicerecorder.domain.SpeechToTransactionService
import rs.smobile.speak2act.feature.voicerecorder.domain.TransactionExecutor
import rs.smobile.speak2act.feature.voicerecorder.domain.VoiceResult
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class RecorderViewModel @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val speechToTransactionService: SpeechToTransactionService,
    private val transactionExecutor: TransactionExecutor,
    private val cloudConsentStore: CloudConsentStore,
    private val demoTalk: DemoTalk,
    private val onDeviceModel: OnDeviceTransactionModel,
) : ViewModel() {

    private companion object {
        private const val TAG = "RecorderViewModel"

        // Ignore accidental sub-100ms taps (16 kHz sample rate).
        private const val MIN_SAMPLES = WhisperAudioRecorder.SAMPLE_RATE_HZ / 10

        // Cap the live waveform history so it doesn't grow unbounded during long recordings.
        private const val MAX_AMPLITUDE_BARS = 96

        private const val NOT_UNDERSTOOD_MESSAGE =
            "Didn't catch that — try “Send 10 francs to Stefan”. Nothing was sent."
    }

    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes = _amplitudes.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private var recordingJob: Job? = null

    private val _uiState = MutableStateFlow<VoiceRecorderUiState>(VoiceRecorderUiState.Initial)
    val uiState: StateFlow<VoiceRecorderUiState> = _uiState

    /** Whether the screen shows talk 2's hybrid details: consent dialog, route badge, "Heard" line. */
    val showsHybridDetails: Boolean = demoTalk.showsHybridDetails

    /** The user's cloud-fallback consent: `null` = not asked yet. */
    val cloudConsent: StateFlow<Boolean?> = cloudConsentStore.granted

    /** Talk 3 only: shows the on-device model's status line. */
    val showsOnDeviceModel: Boolean = demoTalk.usesOnDeviceLlm
    val onDeviceModelStatus: StateFlow<OnDeviceModelStatus> = onDeviceModel.status

    init {
        // Download on Wi-Fi and load the engine while the user gets ready to speak: never mid-command.
        // prepare() returns at once; onCleared() → speechToTransactionService.release() is its pair.
        if (showsOnDeviceModel) onDeviceModel.prepare()
    }

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

    /**
     * Human-in-the-loop step: executes the detected transaction only after the user confirms it.
     */
    fun confirmTransaction() {
        // Route details are optional: talk 1 and CLOUD_AUDIO results have no route, transcript or latency.
        val proposal = _uiState.value as? VoiceRecorderUiState.Success ?: return
        val (transaction, route, heard, latency) = proposal
        _uiState.value = VoiceRecorderUiState.Executing(transaction, route, heard, latency)
        viewModelScope.launch {
            transactionExecutor.execute(transaction).fold(
                onSuccess = {
                    _uiState.value =
                        VoiceRecorderUiState.Executed(transaction, route, heard, latency)
                },
                onFailure = {
                    _uiState.value = VoiceRecorderUiState.Error(it.message ?: "Transaction failed")
                },
            )
        }
    }

    /**
     * Discards the detected transaction without executing it.
     */
    fun cancelTransaction() {
        _uiState.value = VoiceRecorderUiState.Initial
        _amplitudes.value = emptyList()
    }

    /** Stores the user's answer to the one-time cloud consent dialog. */
    fun setCloudConsent(granted: Boolean) {
        viewModelScope.launch { cloudConsentStore.set(granted) }
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
                    // Once the one-time download is complete, the rest (loading, transcribing) is the spinner again.
                    _uiState.value = if (progress < 1f) {
                        VoiceRecorderUiState.DownloadingModel(progress)
                    } else {
                        VoiceRecorderUiState.Loading
                    }
                }
            }

            result.fold(onSuccess = { voiceResult ->
                _uiState.value = voiceResult.toUiState()
            }, onFailure = { t ->
                Log.e(TAG, "Voice command failed", t)
                _uiState.value = VoiceRecorderUiState.Error(t.message ?: "Unknown error")
            })
        }
    }

    private fun VoiceResult.toUiState(): VoiceRecorderUiState = when (this) {
        is VoiceResult.Proposed ->
            if (showsHybridDetails) {
                VoiceRecorderUiState.Success(transaction, source, transcript, timings.total)
            } else {
                VoiceRecorderUiState.Success(transaction)
            }
        // Talk 1 shows clarifying questions the same way.
        is VoiceResult.AskBack -> VoiceRecorderUiState.Error(question)
        is VoiceResult.NotUnderstood -> VoiceRecorderUiState.Error(
            NOT_UNDERSTOOD_MESSAGE,
            heard = transcript.takeIf { it.isNotBlank() && showsHybridDetails }
        )
    }

}
