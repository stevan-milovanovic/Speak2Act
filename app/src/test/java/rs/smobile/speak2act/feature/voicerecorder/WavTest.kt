package rs.smobile.speak2act.feature.voicerecorder

import org.junit.Assert.assertEquals
import org.junit.Test
import rs.smobile.speak2act.feature.voicerecorder.data.toWav
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavTest {

    @Test
    fun writesRiffWaveHeader() {
        val samples = FloatArray(10)
        val wav = ByteBuffer.wrap(samples.toWav()).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RIFF", wav.ascii(0))
        assertEquals(36 + 20, wav.getInt(4))
        assertEquals("WAVE", wav.ascii(8))
        assertEquals("fmt ", wav.ascii(12))
        assertEquals(16, wav.getInt(16))
        assertEquals(1.toShort(), wav.getShort(20))          // PCM
        assertEquals(1.toShort(), wav.getShort(22))          // mono
        assertEquals(16_000, wav.getInt(24))                 // sample rate
        assertEquals(32_000, wav.getInt(28))                 // byte rate
        assertEquals(2.toShort(), wav.getShort(32))          // block align
        assertEquals(16.toShort(), wav.getShort(34))         // bits per sample
        assertEquals("data", wav.ascii(36))
        assertEquals(20, wav.getInt(40))                     // data size
    }

    @Test
    fun lengthIsHeaderPlusTwoBytesPerSample() {
        assertEquals(44, FloatArray(0).toWav().size)
        assertEquals(44 + 2 * 16_000, FloatArray(16_000).toWav().size)
    }

    @Test
    fun clampsSamplesToPcm16Range() {
        val wav = ByteBuffer.wrap(floatArrayOf(2f, -2f, 0f, 1f).toWav()).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(32_767.toShort(), wav.getShort(44))
        assertEquals((-32_767).toShort(), wav.getShort(46))
        assertEquals(0.toShort(), wav.getShort(48))
        assertEquals(32_767.toShort(), wav.getShort(50))
    }

    private fun ByteBuffer.ascii(offset: Int): String =
        String(ByteArray(4) { get(offset + it) }, Charsets.US_ASCII)
}
