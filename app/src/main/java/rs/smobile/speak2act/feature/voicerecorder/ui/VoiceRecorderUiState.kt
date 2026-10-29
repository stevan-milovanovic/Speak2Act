package rs.smobile.speak2act.feature.voicerecorder.ui

import rs.smobile.speak2act.feature.voicerecorder.domain.InferenceRoute
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction
import kotlin.time.Duration

sealed interface VoiceRecorderUiState {
    data object Initial : VoiceRecorderUiState
    data class DownloadingModel(val progress: Float) : VoiceRecorderUiState
    data object Loading : VoiceRecorderUiState

    /**
     * A transaction waiting for the user's confirmation. [route] and [latency] drive the route
     * badge; [heard] is the on-device transcript, when there is one.
     */
    data class Success(
        val transaction: Transaction,
        val route: InferenceRoute? = null,
        val heard: String? = null,
        val latency: Duration? = null,
    ) : VoiceRecorderUiState

    /** The user confirmed [transaction]; it is being executed. */
    data class Executing(
        val transaction: Transaction,
        val route: InferenceRoute? = null,
        val heard: String? = null,
        val latency: Duration? = null,
    ) : VoiceRecorderUiState

    /** [transaction] was executed successfully. */
    data class Executed(
        val transaction: Transaction,
        val route: InferenceRoute? = null,
        val heard: String? = null,
        val latency: Duration? = null,
    ) : VoiceRecorderUiState

    data class Error(val response: String, val heard: String? = null) : VoiceRecorderUiState
}
