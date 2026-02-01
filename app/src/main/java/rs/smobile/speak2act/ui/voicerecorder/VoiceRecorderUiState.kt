package rs.smobile.speak2act.ui.voicerecorder

import rs.smobile.speak2act.domain.Transaction

sealed interface VoiceRecorderUiState {
    data object Initial : VoiceRecorderUiState
    data object Loading : VoiceRecorderUiState
    data class Success(val transaction: Transaction?) : VoiceRecorderUiState

    data class Error(val response: String) : VoiceRecorderUiState
}