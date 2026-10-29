package rs.smobile.speak2act.core.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import rs.smobile.speak2act.core.privacy.CloudConsentStore

class DefaultRoutingConfigTest {

    private class FakeConsent(value: Boolean?) : CloudConsentStore {
        private val state = MutableStateFlow(value)
        override val granted: StateFlow<Boolean?> = state
        override suspend fun set(granted: Boolean) {
            state.value = granted
        }
    }

    private fun config(talk: DemoTalk, consent: Boolean?) = DefaultRoutingConfig(FakeConsent(consent), talk)

    @Test
    fun talk1_alwaysSendsAudioToTheCloud_withTalk1Model() {
        for (consent in listOf(null, false, true)) {
            val config = config(DemoTalk.AGENTS, consent)
            assertEquals(VoiceRoutingMode.CLOUD_AUDIO, config.route)
            assertEquals(TALK_1_MODEL_NAME, config.modelName)
        }
    }

    @Test
    fun talk2_staysOnDeviceUntilConsentIsGranted() {
        assertEquals(VoiceRoutingMode.EDGE_ONLY, config(DemoTalk.EDGE_AND_SERVERLESS, null).route)
        assertEquals(VoiceRoutingMode.EDGE_ONLY, config(DemoTalk.EDGE_AND_SERVERLESS, false).route)
        assertEquals(DEFAULT_VOICE_ROUTE, config(DemoTalk.EDGE_AND_SERVERLESS, true).route)
    }

    @Test
    fun talk2_usesDefaultModel() {
        assertEquals(DEFAULT_MODEL_NAME, config(DemoTalk.EDGE_AND_SERVERLESS, true).modelName)
    }
}
