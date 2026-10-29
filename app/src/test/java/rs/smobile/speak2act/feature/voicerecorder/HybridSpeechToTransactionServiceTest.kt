package rs.smobile.speak2act.feature.voicerecorder

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
        private val AUDIO = FloatArray(16_000)
        private val CLOUD_TRANSACTION =
            Transaction(TransactionAction.SEND, BigDecimal("10"), "CHF", "Stefan", "pizza")
    }

    private class FakeTranscriber(private val transcript: String) : Transcriber {
        var calls = 0
        override suspend fun transcribe(audio: FloatArray, onModelProgress: (Float) -> Unit): String {
            calls++
            return transcript
        }

        override fun release() = Unit
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

    private class FakeRouting(override val route: VoiceRoutingMode) : RoutingConfig {
        override val modelName = "test-model"
    }

    private class FakeNetwork(private val online: Boolean) : NetworkMonitor {
        override fun isOnline() = online
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
    ) = HybridSpeechToTransactionService(
        whisper = transcriber,
        grammar = VoiceTransactionParser(),
        cloud = cloud,
        routing = FakeRouting(route),
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
}
