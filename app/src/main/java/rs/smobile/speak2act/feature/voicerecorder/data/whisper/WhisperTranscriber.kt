package rs.smobile.speak2act.feature.voicerecorder.data.whisper

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rs.smobile.speak2act.BuildConfig
import rs.smobile.speak2act.feature.voicerecorder.domain.Transcriber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.TimeSource

/**
 * On-device [Transcriber] backed by whisper.cpp: downloads/caches the Whisper model on first use,
 * keeps one native context loaded and transcribes locally.
 */
@Singleton
class WhisperTranscriber @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) : Transcriber {

    private val modelDownloader = WhisperModelDownloader(appContext.filesDir)

    private val contextMutex = Mutex()
    private var whisperContext: WhisperContext? = null

    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun transcribe(audio: FloatArray, onModelProgress: (Float) -> Unit): String {
        val context = ensureContext(onModelProgress)
        val language = appWhisperLanguage()
        val mark = TimeSource.Monotonic.markNow()
        val transcript = context.transcribe(audio, language)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "whisper_inference=${mark.elapsedNow().inWholeMilliseconds}ms language=$language")
        }
        return transcript
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

    private suspend fun ensureContext(onModelProgress: (Float) -> Unit): WhisperContext =
        contextMutex.withLock {
            whisperContext ?: run {
                val modelFile = modelDownloader.ensureModel(onModelProgress)
                // Measured after the download: model_load = SHA-256 check + native context creation.
                val mark = TimeSource.Monotonic.markNow()
                WhisperContext.create(modelFile).also {
                    whisperContext = it
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "model_load=${mark.elapsedNow().inWholeMilliseconds}ms")
                    }
                }
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
        private const val TAG = "Speak2ActRoute"
    }
}
