package rs.smobile.speak2act.feature.voicerecorder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.smobile.speak2act.core.config.RoutingConfig
import rs.smobile.speak2act.core.config.VoiceRoutingMode
import rs.smobile.speak2act.core.logging.RouteLogger
import rs.smobile.speak2act.core.network.NetworkMonitor
import rs.smobile.speak2act.feature.voicerecorder.data.HybridSpeechToTransactionService
import rs.smobile.speak2act.feature.voicerecorder.domain.CloudTransactionService
import rs.smobile.speak2act.feature.voicerecorder.domain.InferenceRoute
import rs.smobile.speak2act.feature.voicerecorder.domain.OnDeviceModelStatus
import rs.smobile.speak2act.feature.voicerecorder.domain.OnDeviceTransactionModel
import rs.smobile.speak2act.feature.voicerecorder.domain.ProposalGuard
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction
import rs.smobile.speak2act.feature.voicerecorder.domain.TransactionAction
import rs.smobile.speak2act.feature.voicerecorder.domain.Transcriber
import rs.smobile.speak2act.feature.voicerecorder.domain.VoiceResult
import rs.smobile.speak2act.feature.voicerecorder.domain.VoiceTransactionParser
import java.math.BigDecimal

class HybridSpeechToTransactionServiceTest {

    private companion object {
        private const val EDGE_SENTENCE = "Request 10 euros from Marija for the taxi bill."
        private const val CLOUD_SENTENCE = "I owe Stefan ten francs for the pizza."
        private const val LLM_SENTENCE = CLOUD_SENTENCE
        private val AUDIO = FloatArray(16_000)
        private val CLOUD_TRANSACTION =
            Transaction(TransactionAction.SEND, BigDecimal("10"), "CHF", "Stefan", "pizza")
        private val LLM_TRANSACTION =
            Transaction(TransactionAction.SEND, BigDecimal("10"), "CHF", "Stefan", "pizza")
    }

    private class FakeTranscriber(private val transcript: String) : Transcriber {
        var calls = 0
        var releaseCalls = 0
        override suspend fun transcribe(audio: FloatArray, onModelProgress: (Float) -> Unit): String {
            calls++
            return transcript
        }

        override fun release() {
            releaseCalls++
        }
    }

    private class FakeOnDeviceModel(
        private val result: Transaction?,
        private val throws: Boolean = false,
    ) : OnDeviceTransactionModel {
        val transcripts = mutableListOf<String>()
        var prepareCalls = 0
        var releaseCalls = 0
        val proposeCalls get() = transcripts.size

        override val status: StateFlow<OnDeviceModelStatus> = MutableStateFlow(OnDeviceModelStatus.Ready)

        override fun prepare() {
            prepareCalls++
        }

        override suspend fun propose(transcript: String): Transaction? {
            transcripts += transcript
            if (throws) throw IllegalStateException("LiteRT-LM failed")
            return result
        }

        override fun release() {
            releaseCalls++
        }
    }

    private class FakeCloud : CloudTransactionService {
        val transcripts = mutableListOf<String>()
        var audioCalls = 0
        var lastWavSize = 0

        override suspend fun fromTranscript(transcript: String): VoiceResult {
            transcripts += transcript
            return VoiceResult.Proposed(CLOUD_TRANSACTION, InferenceRoute.CLOUD_TEXT, transcript)
        }

        override suspend fun fromAudio(wav: ByteArray): VoiceResult {
            audioCalls++
            lastWavSize = wav.size
            return VoiceResult.Proposed(CLOUD_TRANSACTION, InferenceRoute.CLOUD_AUDIO)
        }
    }

    private class FakeRouting(
        override val route: VoiceRoutingMode,
        override val localLlmEnabled: Boolean = false,
    ) : RoutingConfig {
        override val modelName = "test-model"
    }

    private class FakeNetwork(private val online: Boolean) : NetworkMonitor {
        override fun isOnline() = online
        override fun isUnmetered() = online
    }

    private class FakeLogger : RouteLogger {
        val messages = mutableListOf<String>()
        override fun log(message: String) {
            messages += message
        }
    }

    private val cloud = FakeCloud()
    private val logger = FakeLogger()

    private fun service(
        transcriber: Transcriber,
        route: VoiceRoutingMode = VoiceRoutingMode.EDGE_THEN_CLOUD,
        online: Boolean = true,
        localLlm: Boolean = false,
        model: OnDeviceTransactionModel = FakeOnDeviceModel(null),
    ) = HybridSpeechToTransactionService(
        whisper = transcriber,
        grammar = VoiceTransactionParser(),
        onDeviceModel = model,
        guard = ProposalGuard(VoiceTransactionParser()),
        cloud = cloud,
        routing = FakeRouting(route, localLlm),
        network = FakeNetwork(online),
        logger = logger,
    )

    @Test
    fun grammarMatch_returnsEdgeResult_andNeverCallsCloud() = runTest {
        val result = service(FakeTranscriber(EDGE_SENTENCE)).parse(AUDIO) {}

        result as VoiceResult.Proposed
        assertEquals(InferenceRoute.EDGE, result.source)
        assertEquals(EDGE_SENTENCE, result.transcript)
        assertEquals("Marija", result.transaction.receiverName)
        assertTrue(cloud.transcripts.isEmpty())
        assertEquals(0, cloud.audioCalls)
    }

    @Test
    fun grammarMiss_online_edgeThenCloud_sendsTranscriptToCloud() = runTest {
        val result = service(FakeTranscriber(CLOUD_SENTENCE)).parse(AUDIO) {}

        assertEquals(listOf(CLOUD_SENTENCE), cloud.transcripts)
        assertEquals(InferenceRoute.CLOUD_TEXT, (result as VoiceResult.Proposed).source)
    }

    @Test
    fun grammarMiss_offline_returnsNotUnderstood_withoutCloudCall() = runTest {
        val result = service(FakeTranscriber(CLOUD_SENTENCE), online = false).parse(AUDIO) {}

        assertEquals(CLOUD_SENTENCE, (result as VoiceResult.NotUnderstood).transcript)
        assertTrue(cloud.transcripts.isEmpty())
    }

    @Test
    fun grammarMiss_edgeOnly_returnsNotUnderstood_withoutCloudCall() = runTest {
        val result = service(FakeTranscriber(CLOUD_SENTENCE), route = VoiceRoutingMode.EDGE_ONLY).parse(AUDIO) {}

        assertTrue(result is VoiceResult.NotUnderstood)
        assertTrue(cloud.transcripts.isEmpty())
    }

    @Test
    fun cloudAudio_online_sendsWav_andNeverCallsWhisper() = runTest {
        val transcriber = FakeTranscriber(EDGE_SENTENCE)

        val result = service(transcriber, route = VoiceRoutingMode.CLOUD_AUDIO).parse(AUDIO) {}

        assertEquals(InferenceRoute.CLOUD_AUDIO, (result as VoiceResult.Proposed).source)
        assertEquals(1, cloud.audioCalls)
        assertEquals(44 + 2 * AUDIO.size, cloud.lastWavSize)
        assertEquals(0, transcriber.calls)
    }

    @Test
    fun cloudAudio_offline_fallsBackToEdge() = runTest {
        val transcriber = FakeTranscriber(EDGE_SENTENCE)

        val result = service(transcriber, route = VoiceRoutingMode.CLOUD_AUDIO, online = false).parse(AUDIO) {}

        assertEquals(InferenceRoute.EDGE, (result as VoiceResult.Proposed).source)
        assertEquals(1, transcriber.calls)
        assertEquals(0, cloud.audioCalls)
    }

    @Test
    fun timings_areSetForTheStagesThatRan() = runTest {
        val edge = service(FakeTranscriber(EDGE_SENTENCE)).parse(AUDIO) {}
        assertNotNull(edge.timings.transcribe)
        assertNotNull(edge.timings.grammar)
        assertNull(edge.timings.cloud)

        val cloudText = service(FakeTranscriber(CLOUD_SENTENCE)).parse(AUDIO) {}
        assertNotNull(cloudText.timings.transcribe)
        assertNotNull(cloudText.timings.grammar)
        assertNotNull(cloudText.timings.cloud)

        val cloudAudio = service(FakeTranscriber(EDGE_SENTENCE), route = VoiceRoutingMode.CLOUD_AUDIO).parse(AUDIO) {}
        assertNull(cloudAudio.timings.transcribe)
        assertNull(cloudAudio.timings.grammar)
        assertNotNull(cloudAudio.timings.cloud)
    }

    @Test
    fun routeLog_containsNoTranscript() = runTest {
        service(FakeTranscriber(EDGE_SENTENCE)).parse(AUDIO) {}

        assertEquals(1, logger.messages.size)
        assertTrue(logger.messages.single().contains("source=EDGE"))
        assertTrue(!logger.messages.single().contains("Marija"))
    }

    // --- On-device language model stage (talk 3) ---

    @Test
    fun grammarHit_neverCallsTheOnDeviceModel() = runTest {
        val model = FakeOnDeviceModel(LLM_TRANSACTION)

        val result = service(FakeTranscriber(EDGE_SENTENCE), localLlm = true, model = model).parse(AUDIO) {}

        assertEquals(InferenceRoute.EDGE, (result as VoiceResult.Proposed).source)
        assertEquals(0, model.proposeCalls)
    }

    @Test
    fun grammarMiss_groundedProposal_returnsEdgeLlm_withoutCloudCall() = runTest {
        for ((route, online) in listOf(
            VoiceRoutingMode.EDGE_THEN_CLOUD to true,
            VoiceRoutingMode.EDGE_THEN_CLOUD to false,
            VoiceRoutingMode.EDGE_ONLY to true,
        )) {
            val model = FakeOnDeviceModel(LLM_TRANSACTION)

            val result = service(FakeTranscriber(LLM_SENTENCE), route, online, localLlm = true, model = model)
                .parse(AUDIO) {}

            result as VoiceResult.Proposed
            assertEquals(InferenceRoute.EDGE_LLM, result.source)
            assertEquals(LLM_SENTENCE, result.transcript)
            assertEquals(LLM_TRANSACTION, result.transaction)
            assertEquals(listOf(LLM_SENTENCE), model.transcripts)
        }
        assertTrue(cloud.transcripts.isEmpty())
    }

    @Test
    fun inventedAmount_isRejected_thenCloudOnline_orNotUnderstoodOffline() = runTest {
        val invented = LLM_TRANSACTION.copy(amount = BigDecimal("100"))

        val online = service(FakeTranscriber(LLM_SENTENCE), localLlm = true, model = FakeOnDeviceModel(invented))
            .parse(AUDIO) {}
        assertEquals(InferenceRoute.CLOUD_TEXT, (online as VoiceResult.Proposed).source)

        val offline = service(
            FakeTranscriber(LLM_SENTENCE), online = false, localLlm = true, model = FakeOnDeviceModel(invented)
        ).parse(AUDIO) {}
        assertTrue(offline is VoiceResult.NotUnderstood)

        assertTrue(logger.messages.any { it.contains("llm_rejected reason=amount_not_spoken") })
        assertTrue(logger.messages.none { it.contains("Stefan") })
    }

    @Test
    fun twoAmounts_areRejectedAsAmbiguous() = runTest {
        val transfer = "Transfer 100 francs to Stefan and 50 to Maria."
        val proposal = LLM_TRANSACTION.copy(amount = BigDecimal("100"), message = null)

        val result = service(FakeTranscriber(transfer), online = false, localLlm = true, model = FakeOnDeviceModel(proposal))
            .parse(AUDIO) {}

        assertTrue(result is VoiceResult.NotUnderstood)
        assertTrue(logger.messages.any { it.contains("llm_rejected reason=amount_ambiguous") })
    }

    @Test
    fun equalAmountsForTwoPeople_areRejectedAsAmbiguous() = runTest {
        val owe = "I owe Ana 10 francs and Bo 10 francs."
        val proposal = Transaction(TransactionAction.SEND, BigDecimal("10"), "CHF", "Ana")

        val result = service(FakeTranscriber(owe), online = false, localLlm = true, model = FakeOnDeviceModel(proposal))
            .parse(AUDIO) {}

        assertTrue(result is VoiceResult.NotUnderstood)
        assertTrue(logger.messages.any { it.contains("llm_rejected reason=amount_ambiguous") })
    }

    @Test
    fun currencyChecks_rejectUnsupportedAndMismatchedCurrencies() = runTest {
        val yen = Transaction(TransactionAction.SEND, BigDecimal("35"), "CHF", "David")
        service(FakeTranscriber("I owe David 35 yen."), online = false, localLlm = true, model = FakeOnDeviceModel(yen))
            .parse(AUDIO) {}
        assertTrue(logger.messages.any { it.contains("llm_rejected reason=currency_unsupported") })

        val euros = LLM_TRANSACTION.copy(currency = "EUR")
        service(FakeTranscriber("I owe Stefan 10 for the pizza."), online = false, localLlm = true, model = FakeOnDeviceModel(euros))
            .parse(AUDIO) {}
        assertTrue(logger.messages.any { it.contains("llm_rejected reason=currency_mismatch") })
    }

    @Test
    fun noProposal_offlineNotUnderstood_onlineCloudText() = runTest {
        val offline = service(FakeTranscriber(LLM_SENTENCE), online = false, localLlm = true, model = FakeOnDeviceModel(null))
            .parse(AUDIO) {}
        assertTrue(offline is VoiceResult.NotUnderstood)

        val online = service(FakeTranscriber(LLM_SENTENCE), localLlm = true, model = FakeOnDeviceModel(null))
            .parse(AUDIO) {}
        assertEquals(InferenceRoute.CLOUD_TEXT, (online as VoiceResult.Proposed).source)
    }

    @Test
    fun modelError_isTreatedLikeNoProposal_andLogged() = runTest {
        val result = service(
            FakeTranscriber(LLM_SENTENCE), online = false, localLlm = true,
            model = FakeOnDeviceModel(LLM_TRANSACTION, throws = true)
        ).parse(AUDIO) {}

        assertTrue(result is VoiceResult.NotUnderstood)
        assertTrue(logger.messages.any { it.contains("llm_failed") })
    }

    @Test
    fun localLlmDisabled_neverCallsTheModel() = runTest {
        val model = FakeOnDeviceModel(LLM_TRANSACTION)

        val result = service(FakeTranscriber(LLM_SENTENCE), online = false, localLlm = false, model = model)
            .parse(AUDIO) {}

        assertTrue(result is VoiceResult.NotUnderstood)
        assertEquals(0, model.proposeCalls)
    }

    @Test
    fun llmTiming_isSetOnlyWhenTheStageRan() = runTest {
        val edge = service(FakeTranscriber(EDGE_SENTENCE), localLlm = true).parse(AUDIO) {}
        assertNull(edge.timings.llm)

        val llm = service(FakeTranscriber(LLM_SENTENCE), localLlm = true, model = FakeOnDeviceModel(LLM_TRANSACTION))
            .parse(AUDIO) {}
        assertNotNull(llm.timings.llm)
        assertNull(llm.timings.cloud)

        val talk2 = service(FakeTranscriber(LLM_SENTENCE), localLlm = false).parse(AUDIO) {}
        assertNull(talk2.timings.llm)
    }

    @Test
    fun routeLog_containsTheLlmStage() = runTest {
        service(FakeTranscriber(LLM_SENTENCE), localLlm = true, model = FakeOnDeviceModel(LLM_TRANSACTION))
            .parse(AUDIO) {}

        val line = logger.messages.single()
        assertTrue(line.contains("source=EDGE_LLM"))
        assertTrue(line.contains(" llm="))
        assertTrue(!line.contains("Stefan"))
    }

    @Test
    fun release_releasesWhisperAndTheOnDeviceModel() {
        val transcriber = FakeTranscriber(EDGE_SENTENCE)
        val model = FakeOnDeviceModel(null)

        service(transcriber, localLlm = true, model = model).release()

        assertEquals(1, transcriber.releaseCalls)
        assertEquals(1, model.releaseCalls)
        assertEquals(0, model.prepareCalls)
    }
}
