package rs.smobile.speak2act.core.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
            assertFalse(config.localLlmEnabled)
        }
    }

    @Test
    fun talk2_staysOnDeviceUntilConsentIsGranted() {
        assertEquals(VoiceRoutingMode.EDGE_ONLY, config(DemoTalk.EDGE_AND_SERVERLESS, null).route)
        assertEquals(VoiceRoutingMode.EDGE_ONLY, config(DemoTalk.EDGE_AND_SERVERLESS, false).route)
        assertEquals(DEFAULT_VOICE_ROUTE, config(DemoTalk.EDGE_AND_SERVERLESS, true).route)
    }

    @Test
    fun talk2_usesDefaultModel_withoutTheOnDeviceModel() {
        assertEquals(DEFAULT_MODEL_NAME, config(DemoTalk.EDGE_AND_SERVERLESS, true).modelName)
        for (consent in listOf(null, false, true)) {
            assertFalse(config(DemoTalk.EDGE_AND_SERVERLESS, consent).localLlmEnabled)
        }
    }

    @Test
    fun talk3_enablesTheOnDeviceModel_andKeepsConsentRouting() {
        for (consent in listOf(null, false, true)) {
            assertTrue(config(DemoTalk.ON_DEVICE_LLM, consent).localLlmEnabled)
        }
        assertEquals(VoiceRoutingMode.EDGE_ONLY, config(DemoTalk.ON_DEVICE_LLM, null).route)
        assertEquals(VoiceRoutingMode.EDGE_ONLY, config(DemoTalk.ON_DEVICE_LLM, false).route)
        assertEquals(DEFAULT_VOICE_ROUTE, config(DemoTalk.ON_DEVICE_LLM, true).route)
        assertEquals(DEFAULT_MODEL_NAME, config(DemoTalk.ON_DEVICE_LLM, true).modelName)
    }
}
