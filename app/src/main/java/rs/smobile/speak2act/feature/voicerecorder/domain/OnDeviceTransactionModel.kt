package rs.smobile.speak2act.feature.voicerecorder.domain

import kotlinx.coroutines.flow.StateFlow

/** Status of the on-device language model, for the recorder screen's status line. */
sealed interface OnDeviceModelStatus {
    /** Not prepared yet (or released). */
    data object Idle : OnDeviceModelStatus

    /** Not downloaded, and the network is metered or offline. */
    data object Missing : OnDeviceModelStatus

    data class Downloading(val progress: Float) : OnDeviceModelStatus
    data object Loading : OnDeviceModelStatus
    data object Ready : OnDeviceModelStatus

    /** [reason] is a fixed code: "not_published", "download_failed", "init_failed", "disabled". */
    data class Unavailable(val reason: String) : OnDeviceModelStatus
}

/** A language model on the device that turns a transcript into a transaction proposal — or nothing. */
interface OnDeviceTransactionModel {
    val status: StateFlow<OnDeviceModelStatus>

    /**
     * Starts downloading (unmetered networks only) and loading the model in the background, and returns at once.
     * Pair every call with one [release]. Never throws.
     */
    fun prepare()

    /**
     * The model's proposal for [transcript], or `null` when it isn't [OnDeviceModelStatus.Ready], makes no
     * call, or its arguments don't map to a [Transaction]. Never asks back. May throw on runtime errors —
     * the router treats that like `null`.
     */
    suspend fun propose(transcript: String): Transaction?

    /** Ends one [prepare]. When the last owner releases, frees the engine; the file stays on disk. */
    fun release()
}
