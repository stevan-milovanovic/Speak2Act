package rs.smobile.speak2act.feature.voicerecorder.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rs.smobile.speak2act.feature.voicerecorder.data.whisper.WhisperContext
import rs.smobile.speak2act.feature.voicerecorder.data.whisper.WhisperModelDownloader
import rs.smobile.speak2act.feature.voicerecorder.domain.ParsedVoiceTransaction
import rs.smobile.speak2act.feature.voicerecorder.domain.SpeechToTransactionService
import rs.smobile.speak2act.feature.voicerecorder.domain.VoiceTransactionParser
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device implementation of [SpeechToTransactionService]: downloads/caches the Whisper model on
 * first use, transcribes locally with whisper.cpp, then parses the transcript with
 * [VoiceTransactionParser].
 */
@Singleton
class WhisperSpeechToTransactionService @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) : SpeechToTransactionService {

    private val modelDownloader = WhisperModelDownloader(appContext.filesDir)
    private val transactionParser = VoiceTransactionParser()

    private val contextMutex = Mutex()
    private var whisperContext: WhisperContext? = null

    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun parse(
        audio: FloatArray,
        onModelDownloadProgress: (Float) -> Unit,
    ): ParsedVoiceTransaction? {
        val context = ensureContext(onModelDownloadProgress)
        val language = appWhisperLanguage()
        Log.d(TAG, "Transcribing with language \"$language\"")
        val transcript = context.transcribe(audio, language)
        Log.d(TAG, "Transcription result: \"$transcript\"")

        val parsed = transactionParser.parse(transcript)
        if (parsed == null) {
            Log.d(TAG, "Transcript is not a recognized transaction command")
        } else {
            Log.d(
                TAG,
                "Parsed intent: action=${parsed.action} amount=${parsed.amount} " +
                    "receiver=\"${parsed.receiverName}\" currency=${parsed.currency ?: "<none>"} " +
                    "message=${parsed.message?.let { "\"$it\"" } ?: "<none>"}",
            )
        }
        return parsed
    }

    override fun release() {
        releaseScope.launch {
            contextMutex.withLock {
                val context = whisperContext ?: return@withLock
                whisperContext = null
                Log.d(TAG, "Releasing Whisper context")
                context.release()
            }
        }
    }

    private suspend fun ensureContext(onModelDownloadProgress: (Float) -> Unit): WhisperContext =
        contextMutex.withLock {
            whisperContext ?: run {
                val modelFile = modelDownloader.ensureModel(onModelDownloadProgress)
                WhisperContext.create(modelFile).also { whisperContext = it }
            }
        }

    /**
     * Transcribe in the device language so names and words are recognized more reliably; falls
     * back to auto-detect when no usable 2-letter language code is available.
     */
    private fun appWhisperLanguage(): String {
        val code = Locale.getDefault().language.lowercase(Locale.ROOT)
        return if (code.length == 2 && code.all { it.isLetter() }) {
            code
        } else {
            WhisperContext.DEFAULT_LANGUAGE
        }
    }

    private companion object {
        private const val TAG = "WhisperSpeechToTx"
    }
}
