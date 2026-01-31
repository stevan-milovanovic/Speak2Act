package rs.smobile.speak2act.ui.voicerecorder

sealed interface VoiceRecorderUiState {
    data object Initial : VoiceRecorderUiState
    data object Loading : VoiceRecorderUiState
    data class Success(
        val action: String?,
        val amount: Double?,
        val currency: String?,
        val person: String?,
        val description: String?
    ) : VoiceRecorderUiState

    data class Error(val response: String) : VoiceRecorderUiState
}