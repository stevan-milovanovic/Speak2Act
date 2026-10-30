package rs.smobile.speak2act.feature.voicerecorder.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.smobile.speak2act.core.ai.TransactionContract
import rs.smobile.speak2act.feature.voicerecorder.data.llm.TransactionTool

class TransactionToolTest {

    @Test
    fun declaresTheSharedContract() {
        assertEquals(TransactionContract.toolJson, TransactionTool.getToolDescriptionJsonString())
    }

    @Test
    fun execute_throws_theModelOnlyProposes() {
        assertTrue(runCatching { TransactionTool.execute("{}") }.exceptionOrNull() is IllegalStateException)
    }
}
