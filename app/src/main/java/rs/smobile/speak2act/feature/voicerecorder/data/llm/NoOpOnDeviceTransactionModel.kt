package rs.smobile.speak2act.feature.voicerecorder.data.llm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rs.smobile.speak2act.feature.voicerecorder.domain.OnDeviceModelStatus
import rs.smobile.speak2act.feature.voicerecorder.domain.OnDeviceTransactionModel
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction

/** Bound when the talk-3 flag is off (talks 1 and 2): never downloads or loads anything, never proposes. */
object NoOpOnDeviceTransactionModel : OnDeviceTransactionModel {
    override val status: StateFlow<OnDeviceModelStatus> =
        MutableStateFlow(OnDeviceModelStatus.Unavailable("disabled")).asStateFlow()

    override fun prepare() = Unit

    override suspend fun propose(transcript: String): Transaction? = null

    override fun release() = Unit
}
