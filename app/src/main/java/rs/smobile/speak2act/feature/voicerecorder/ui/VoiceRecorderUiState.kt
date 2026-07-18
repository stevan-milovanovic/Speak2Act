package rs.smobile.speak2act.feature.voicerecorder.ui

import rs.smobile.speak2act.feature.voicerecorder.domain.ParsedVoiceTransaction

sealed interface VoiceRecorderUiState {
    data object Initial : VoiceRecorderUiState
    data class DownloadingModel(val progress: Float) : VoiceRecorderUiState
    data object Loading : VoiceRecorderUiState
    data class Success(val transaction: ParsedVoiceTransaction?) : VoiceRecorderUiState

    data class Error(val response: String) : VoiceRecorderUiState
}
