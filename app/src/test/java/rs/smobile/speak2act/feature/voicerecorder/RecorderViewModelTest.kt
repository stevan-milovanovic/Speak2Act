package rs.smobile.speak2act.feature.voicerecorder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import rs.smobile.speak2act.core.config.DemoTalk
import rs.smobile.speak2act.core.privacy.CloudConsentStore
import rs.smobile.speak2act.feature.voicerecorder.domain.AudioRecorder
import rs.smobile.speak2act.feature.voicerecorder.domain.InferenceRoute
import rs.smobile.speak2act.feature.voicerecorder.domain.OnDeviceModelStatus
import rs.smobile.speak2act.feature.voicerecorder.domain.OnDeviceTransactionModel
import rs.smobile.speak2act.feature.voicerecorder.domain.SpeechToTransactionService
import rs.smobile.speak2act.feature.voicerecorder.domain.StageTimings
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction
import rs.smobile.speak2act.feature.voicerecorder.domain.TransactionAction
import rs.smobile.speak2act.feature.voicerecorder.domain.TransactionExecutor
import rs.smobile.speak2act.feature.voicerecorder.domain.VoiceResult
import rs.smobile.speak2act.feature.voicerecorder.ui.RecorderViewModel
import rs.smobile.speak2act.feature.voicerecorder.ui.VoiceRecorderUiState
import java.math.BigDecimal
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RecorderViewModelTest {

    private companion object {
        private val TRANSACTION = Transaction(
            action = TransactionAction.REQUEST,
            amount = BigDecimal("10"),
            currency = "EUR",
            receiverName = "Marija",
            message = "for the taxi bill"
        )
        private const val TRANSCRIPT = "Request 10 euros from Marija for the taxi bill."
        private val LATENCY = 800.milliseconds
        private val PROPOSED = VoiceResult.Proposed(
            TRANSACTION, InferenceRoute.EDGE, TRANSCRIPT, StageTimings(total = LATENCY)
        )
        private const val EXECUTION_DELAY_MS = 500L
        private const val STEP_MS = 100L

        // 1 s of 16 kHz audio: above the 100 ms minimum.
        private val AUDIO = FloatArray(16_000)
    }

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        voiceResult: VoiceResult = PROPOSED,
        executorResult: Result<Unit> = Result.success(Unit),
        demoTalk: DemoTalk = DemoTalk.EDGE_AND_SERVERLESS,
        onDeviceModel: FakeOnDeviceModel = FakeOnDeviceModel(),
        modelProgress: List<Float> = emptyList(),
    ) = RecorderViewModel(
        FakeAudioRecorder(AUDIO),
        FakeSpeechToTransactionService(voiceResult, modelProgress),
        FakeTransactionExecutor(executorResult),
        FakeCloudConsentStore(),
        demoTalk,
        onDeviceModel,
    )

    @Test
    fun stopRecording_withCachedModel_showsTheSpinner_neverADownload() = runTest {
        val vm = createViewModel()

        vm.startRecording()
        vm.stopRecording()
        runCurrent()

        assertEquals(VoiceRecorderUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        assertEquals(
            VoiceRecorderUiState.Success(TRANSACTION, InferenceRoute.EDGE, TRANSCRIPT, LATENCY),
            vm.uiState.value
        )
    }

    @Test
    fun stopRecording_firstUseDownload_showsProgress_thenTheSpinner() = runTest {
        val vm = createViewModel(modelProgress = listOf(0.5f, 1f))

        vm.startRecording()
        vm.stopRecording()
        runCurrent()
        assertEquals(VoiceRecorderUiState.DownloadingModel(0.5f), vm.uiState.value)

        advanceTimeBy(STEP_MS)
        runCurrent()
        assertEquals(VoiceRecorderUiState.Loading, vm.uiState.value)   // downloaded: loading and transcribing

        advanceUntilIdle()
        assertEquals(
            VoiceRecorderUiState.Success(TRANSACTION, InferenceRoute.EDGE, TRANSCRIPT, LATENCY),
            vm.uiState.value
        )
    }

    @Test
    fun talk3_init_preparesTheOnDeviceModel() {
        val model = FakeOnDeviceModel()

        val vm = createViewModel(demoTalk = DemoTalk.ON_DEVICE_LLM, onDeviceModel = model)

        assertEquals(1, model.prepareCalls)
        assertTrue(vm.showsOnDeviceModel)
        assertEquals(OnDeviceModelStatus.Loading, vm.onDeviceModelStatus.value)
    }

    @Test
    fun talk2_init_doesNotPrepareTheOnDeviceModel() {
        val model = FakeOnDeviceModel()

        val vm = createViewModel(demoTalk = DemoTalk.EDGE_AND_SERVERLESS, onDeviceModel = model)

        assertEquals(0, model.prepareCalls)
        assertFalse(vm.showsOnDeviceModel)
    }

    @Test
    fun stopRecording_withEdgeLlmResult_emitsSuccessWithRoute() = runTest {
        val onDevice = VoiceResult.Proposed(
            TRANSACTION, InferenceRoute.EDGE_LLM, TRANSCRIPT, StageTimings(total = LATENCY)
        )
        val vm = createViewModel(voiceResult = onDevice, demoTalk = DemoTalk.ON_DEVICE_LLM)

        vm.startRecording()
        vm.stopRecording()
        advanceUntilIdle()

        assertEquals(
            VoiceRecorderUiState.Success(TRANSACTION, InferenceRoute.EDGE_LLM, TRANSCRIPT, LATENCY),
            vm.uiState.value
        )
    }

    @Test
    fun stopRecording_withProposedResult_emitsSuccessWithRouteAndLatency() = runTest {
        val vm = createViewModel()

        vm.startRecording()
        vm.stopRecording()
        advanceUntilIdle()

        assertEquals(
            VoiceRecorderUiState.Success(TRANSACTION, InferenceRoute.EDGE, TRANSCRIPT, LATENCY),
            vm.uiState.value
        )
    }

    @Test
    fun talk1_stopRecording_emitsSuccessWithoutHybridDetails() = runTest {
        val vm = createViewModel(demoTalk = DemoTalk.AGENTS)

        vm.startRecording()
        vm.stopRecording()
        advanceUntilIdle()

        assertEquals(VoiceRecorderUiState.Success(TRANSACTION), vm.uiState.value)
        assertEquals(false, vm.showsHybridDetails)
    }

    @Test
    fun stopRecording_withNotUnderstood_emitsErrorWithHeardLine() = runTest {
        val vm = createViewModel(voiceResult = VoiceResult.NotUnderstood("I owe Stefan ten francs"))

        vm.startRecording()
        vm.stopRecording()
        advanceUntilIdle()

        val state = vm.uiState.value as VoiceRecorderUiState.Error
        assertEquals("I owe Stefan ten francs", state.heard)
        assertTrue(state.response.contains("Nothing was sent"))
    }

    @Test
    fun stopRecording_withAskBack_emitsQuestionAsError() = runTest {
        val vm = createViewModel(
            voiceResult = VoiceResult.AskBack("How much should I send to Marija?", InferenceRoute.CLOUD_TEXT)
        )

        vm.startRecording()
        vm.stopRecording()
        advanceUntilIdle()

        assertEquals(VoiceRecorderUiState.Error("How much should I send to Marija?"), vm.uiState.value)
    }

    @Test
    fun confirmTransaction_emitsExecutingThenExecuted() = runTest {
        val vm = createViewModel()
        vm.startRecording()
        vm.stopRecording()
        advanceUntilIdle()

        vm.confirmTransaction()
        runCurrent()
        assertEquals(
            VoiceRecorderUiState.Executing(TRANSACTION, InferenceRoute.EDGE, TRANSCRIPT, LATENCY),
            vm.uiState.value
        )

        advanceUntilIdle()
        assertEquals(
            VoiceRecorderUiState.Executed(TRANSACTION, InferenceRoute.EDGE, TRANSCRIPT, LATENCY),
            vm.uiState.value
        )
    }

    @Test
    fun talk1_confirmTransaction_executes() = runTest {
        val vm = createViewModel(demoTalk = DemoTalk.AGENTS)
        vm.startRecording()
        vm.stopRecording()
        advanceUntilIdle()

        vm.confirmTransaction()
        runCurrent()
        assertEquals(VoiceRecorderUiState.Executing(TRANSACTION), vm.uiState.value)

        advanceUntilIdle()
        assertEquals(VoiceRecorderUiState.Executed(TRANSACTION), vm.uiState.value)
    }

    @Test
    fun confirmTransaction_withoutTranscript_executes() = runTest {
        val cloudAudio = VoiceResult.Proposed(
            TRANSACTION, InferenceRoute.CLOUD_AUDIO, transcript = null, StageTimings(total = LATENCY)
        )
        val vm = createViewModel(voiceResult = cloudAudio)
        vm.startRecording()
        vm.stopRecording()
        advanceUntilIdle()

        vm.confirmTransaction()
        runCurrent()
        assertEquals(
            VoiceRecorderUiState.Executing(TRANSACTION, InferenceRoute.CLOUD_AUDIO, null, LATENCY),
            vm.uiState.value
        )

        advanceUntilIdle()
        assertEquals(
            VoiceRecorderUiState.Executed(TRANSACTION, InferenceRoute.CLOUD_AUDIO, null, LATENCY),
            vm.uiState.value
        )
    }

    @Test
    fun cancelTransaction_resetsToInitial() = runTest {
        val vm = createViewModel()
        vm.startRecording()
        vm.stopRecording()
        advanceUntilIdle()

        vm.cancelTransaction()

        assertEquals(VoiceRecorderUiState.Initial, vm.uiState.value)
        assertTrue(vm.amplitudes.value.isEmpty())
    }

    @Test
    fun confirmTransaction_whenNotInSuccess_isIgnored() = runTest {
        val vm = createViewModel()

        vm.confirmTransaction()
        advanceUntilIdle()

        assertEquals(VoiceRecorderUiState.Initial, vm.uiState.value)
    }

    @Test
    fun setCloudConsent_storesTheAnswer() = runTest {
        val vm = createViewModel()

        vm.setCloudConsent(false)
        advanceUntilIdle()

        assertEquals(false, vm.cloudConsent.value)
    }

    private class FakeAudioRecorder(private val audio: FloatArray) : AudioRecorder {
        override fun start() = Unit
        override fun stop(): FloatArray = audio
        override fun getAmplitude(): Float = 0.5f
    }

    /** Reports each [modelProgress] value [STEP_MS] apart (a first-use download), then returns [result]. */
    private class FakeSpeechToTransactionService(
        private val result: VoiceResult,
        private val modelProgress: List<Float> = emptyList(),
    ) : SpeechToTransactionService {
        override suspend fun parse(audio: FloatArray, onModelProgress: (Float) -> Unit): VoiceResult {
            for (progress in modelProgress) {
                onModelProgress(progress)
                delay(STEP_MS)
            }
            delay(STEP_MS)
            return result
        }

        override fun release() = Unit
    }

    private class FakeTransactionExecutor(private val result: Result<Unit>) : TransactionExecutor {
        override suspend fun execute(transaction: Transaction): Result<Unit> {
            delay(EXECUTION_DELAY_MS)
            return result
        }
    }

    private class FakeOnDeviceModel : OnDeviceTransactionModel {
        var prepareCalls = 0
        override val status: StateFlow<OnDeviceModelStatus> = MutableStateFlow(OnDeviceModelStatus.Loading)
        override fun prepare() {
            prepareCalls++
        }

        override suspend fun propose(transcript: String): Transaction? = null
        override fun release() = Unit
    }

    private class FakeCloudConsentStore : CloudConsentStore {
        private val state = MutableStateFlow<Boolean?>(null)
        override val granted: StateFlow<Boolean?> = state
        override suspend fun set(granted: Boolean) {
            state.value = granted
        }
    }
}
