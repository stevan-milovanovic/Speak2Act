package rs.smobile.speak2act.feature.voicerecorder.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.smobile.speak2act.feature.voicerecorder.data.llm.FunctionGemmaModel

class FunctionGemmaModelTest {

    private val sha256 = Regex("[0-9a-f]{64}")

    @Test
    fun tunedModel_isPublished_andPinned() {
        val tuned = FunctionGemmaModel.TUNED

        assertTrue(tuned.isPublished)
        assertEquals(284_117_632L, tuned.sizeBytes)
        assertEquals("0d5471e79b6677ac7b7a24232ed65326076345d3ec14cc9cc6e94e56d9001f63", tuned.sha256)
    }

    @Test
    fun everyPublishedModel_isAnHttpsFileWithSizeAndHash() {
        for (spec in listOf(FunctionGemmaModel.TUNED, FunctionGemmaModel.GEMMA4_E2B)) {
            assertTrue(spec.url, spec.url.startsWith("https://"))
            assertTrue(spec.url, spec.url.endsWith("/${spec.fileName}"))
            assertTrue(spec.fileName, spec.sizeBytes > 0)
            assertTrue(spec.fileName, sha256.matches(spec.sha256))
        }
    }

    @Test
    fun baseModel_staysUnpublished_forAdbPushedDebugFiles() {
        assertFalse(FunctionGemmaModel.BASE.isPublished)
    }

    @Test
    fun olderTunedVersions_areStale_butOtherModelsAreNot() {
        val isStale = FunctionGemmaModel.TUNED.isStale
        assertTrue(isStale("speak2act-functiongemma-v0.litertlm"))
        assertFalse(isStale("gemma-4-E2B-it.litertlm"))
        assertFalse(isStale("functiongemma-270m-base.litertlm"))
    }
}
