package rs.smobile.speak2act.core.config

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingConfigTest {

    @Test
    fun parseRoute_readsKnownModes() {
        assertEquals(VoiceRoutingMode.EDGE_ONLY, parseRoute("EDGE_ONLY"))
        assertEquals(VoiceRoutingMode.EDGE_THEN_CLOUD, parseRoute("EDGE_THEN_CLOUD"))
        assertEquals(VoiceRoutingMode.CLOUD_AUDIO, parseRoute(" CLOUD_AUDIO "))
    }

    @Test
    fun parseRoute_fallsBackToDefaultForUnknownOrBlankValues() {
        assertEquals(DEFAULT_VOICE_ROUTE, parseRoute(""))
        assertEquals(DEFAULT_VOICE_ROUTE, parseRoute(null))
        assertEquals(DEFAULT_VOICE_ROUTE, parseRoute("cloud_audio"))
        assertEquals(DEFAULT_VOICE_ROUTE, parseRoute("SOMETHING_ELSE"))
    }
}
