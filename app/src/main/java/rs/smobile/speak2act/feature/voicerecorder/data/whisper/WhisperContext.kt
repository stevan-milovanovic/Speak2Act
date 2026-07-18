package rs.smobile.speak2act.feature.voicerecorder.data.whisper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * High level wrapper around [WhisperLib] performing fully on-device transcription with a
 * local whisper.cpp model.
 *
 * The model is downloaded on demand to internal storage (see [WhisperModelDownloader]) and
 * loaded from there by file path so it can be memory-mapped by the native code. All native
 * calls are confined to a single dedicated thread because a whisper context is not thread safe.
 */
class WhisperContext private constructor(private var contextPtr: Long) {

    private val inferenceDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "whisper-inference")
    }.asCoroutineDispatcher()

    /**
     * Transcribes 16 kHz mono float audio ([-1, 1]) and returns the recognised text.
     */
    suspend fun transcribe(
        audioData: FloatArray,
        language: String = DEFAULT_LANGUAGE,
    ): String = withContext(inferenceDispatcher) {
        check(contextPtr != 0L) { "WhisperContext has already been released" }

        val threadCount = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)
        val resultCode = WhisperLib.fullTranscribe(contextPtr, threadCount, audioData, language)
        check(resultCode == 0) { "whisper_full failed with code $resultCode" }

        buildString {
            val segmentCount = WhisperLib.getTextSegmentCount(contextPtr)
            for (index in 0 until segmentCount) {
                append(WhisperLib.getTextSegment(contextPtr, index))
            }
        }.trim()
    }

    suspend fun release() {
        withContext(inferenceDispatcher) {
            if (contextPtr != 0L) {
                WhisperLib.freeContext(contextPtr)
                contextPtr = 0L
            }
        }
        inferenceDispatcher.close()
    }

    companion object {

        private const val TAG = "WhisperContext"

        const val DEFAULT_LANGUAGE = "auto"

        /**
         * Creates a context from an already-downloaded model file.
         * Heavy native work, so it is dispatched off the main thread.
         */
        suspend fun create(modelFile: File): WhisperContext = withContext(Dispatchers.IO) {
            val pointer = WhisperLib.initContext(modelFile.absolutePath)
            check(pointer != 0L) { "Failed to initialise whisper context from $modelFile" }
            Log.d(TAG, "Whisper initialised. System info: ${WhisperLib.getSystemInfo()}")
            WhisperContext(pointer)
        }
    }
}
