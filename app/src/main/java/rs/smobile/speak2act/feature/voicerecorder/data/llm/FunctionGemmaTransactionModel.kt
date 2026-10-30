package rs.smobile.speak2act.feature.voicerecorder.data.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rs.smobile.speak2act.BuildConfig
import rs.smobile.speak2act.core.model.ModelDownloader
import rs.smobile.speak2act.core.network.NetworkMonitor
import rs.smobile.speak2act.feature.voicerecorder.domain.OnDeviceModelStatus
import rs.smobile.speak2act.feature.voicerecorder.domain.OnDeviceTransactionModel
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * [OnDeviceTransactionModel] on LiteRT-LM, CPU backend. The model file is downloaded once on an unmetered network
 * into noBackupFilesDir and pinned like Whisper's. Every command gets a fresh conversation with the shared
 * executeTransaction tool; automatic tool calling is off, so the model only proposes.
 */
@Singleton
class FunctionGemmaTransactionModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val network: NetworkMonitor,
) : OnDeviceTransactionModel {

    private val spec = FunctionGemmaModel.current
    private val modelFile = File(appContext.noBackupFilesDir, spec.fileName)
    private val downloader = ModelDownloader(appContext.noBackupFilesDir, spec)

    private val _status = MutableStateFlow<OnDeviceModelStatus>(OnDeviceModelStatus.Idle)
    override val status: StateFlow<OnDeviceModelStatus> = _status.asStateFlow()

    // LiteRT-LM calls block, and a Conversation isn't documented as thread-safe: every engine call
    // (initialize, conversations, close) runs on this one thread — like the Whisper context.
    private val engineThread =
        Executors.newSingleThreadExecutor { Thread(it, "FunctionGemma") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Loading and closing never overlap — the same rule as WhisperTranscriber's context mutex. */
    private val lifecycle = Mutex()
    private val owners = Any()          // guards ownerCount and prepareJob
    private var ownerCount = 0
    private var prepareJob: Job? = null
    private var engine: Engine? = null  // engineThread only
    private var prefaceLogged = false   // engineThread only

    override fun prepare() {
        synchronized(owners) {
            ownerCount++
            if (prepareJob?.isActive == true) return
            prepareJob = scope.launch { lifecycle.withLock { loadIfNeeded() } }
        }
    }

    override fun release() {
        synchronized(owners) {
            ownerCount = (ownerCount - 1).coerceAtLeast(0)
            if (ownerCount > 0) return
            prepareJob?.cancel()   // stops a download; a running initialize() finishes first
        }
        scope.launch {
            lifecycle.withLock {
                // A late release must not close the engine a newly opened screen prepared meanwhile.
                if (synchronized(owners) { ownerCount > 0 }) return@withLock
                withContext(engineThread) {
                    engine?.close()
                    engine = null
                }
                _status.value = OnDeviceModelStatus.Idle
            }
        }
    }

    override suspend fun propose(transcript: String): Transaction? {
        if (_status.value == OnDeviceModelStatus.Loading) {
            withTimeoutOrNull(LOAD_WAIT) { status.first { it != OnDeviceModelStatus.Loading } }
        }
        if (_status.value != OnDeviceModelStatus.Ready) return null
        return withContext(engineThread) {
            val loaded = engine ?: return@withContext null
            loaded.createConversation(conversationConfig()).use { conversation ->
                if (BuildConfig.DEBUG) logPrefaceOnce(conversation)
                val mark = TimeSource.Monotonic.markNow()
                val reply = conversation.sendMessage(transcript)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "llm_reply=${mark.elapsedNow().inWholeMilliseconds}ms tool_calls=${reply.toolCalls.size}")
                    if (reply.toolCalls.isEmpty()) Log.d(TAG, "llm_text=$reply")   // debug only: may echo the user
                }
                reply.toolCalls.toProposalOrNull()
            }
        }
    }

    private suspend fun loadIfNeeded() {
        if (withContext(engineThread) { engine != null }) {
            _status.value = OnDeviceModelStatus.Ready
            return
        }
        if (!ensureDownloaded()) return
        _status.value = OnDeviceModelStatus.Loading
        _status.value = withContext(engineThread) { loadEngine() }
    }

    /** True when the file is ready to load. A cached file is verified offline; downloads need an unmetered network. */
    private suspend fun ensureDownloaded(): Boolean = try {
        when {
            !spec.isPublished -> {
                // Unpublished (the base variant): debug builds may load a file pushed with adb (§8.5.5) — unverified.
                val pushed = BuildConfig.DEBUG && withContext(Dispatchers.IO) { modelFile.isFile }
                if (pushed) Log.w(TAG, "Loading an unpinned model file: ${spec.fileName}")
                else _status.value = OnDeviceModelStatus.Unavailable("not_published")
                pushed
            }
            downloader.verifyCached() -> true                    // marker, or a one-time hash: no network
            !network.isUnmetered() -> false.also { _status.value = OnDeviceModelStatus.Missing }
            else -> {
                downloader.ensureModel { _status.value = OnDeviceModelStatus.Downloading(it) }
                true
            }
        }
    } catch (e: CancellationException) {
        _status.value = OnDeviceModelStatus.Idle
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Model download failed", e)
        _status.value = OnDeviceModelStatus.Unavailable("download_failed")
        false
    }

    /** Debug check of what the phone renders (plan §8.5.5): the system prompt and the tool — no user data. */
    @OptIn(ExperimentalApi::class)
    private fun logPrefaceOnce(conversation: Conversation) {
        if (prefaceLogged) return
        prefaceLogged = true
        runCatching { Log.d(TAG, "llm_preface=${conversation.renderPrefaceIntoString()}") }
    }

    private fun loadEngine(): OnDeviceModelStatus = try {
        val mark = TimeSource.Monotonic.markNow()
        engine = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),              // Pixel 6: CPU only (plan §5.4)
                cacheDir = appContext.cacheDir.path,  // faster second load
            )
        ).also { it.initialize() }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "llm_engine_init=${mark.elapsedNow().inWholeMilliseconds}ms model=${spec.fileName}")
        }
        OnDeviceModelStatus.Ready
    } catch (e: Exception) {
        Log.w(TAG, "LiteRT-LM engine failed to start", e)
        OnDeviceModelStatus.Unavailable("init_failed")
    }

    private fun conversationConfig() = ConversationConfig(
        systemInstruction = Contents.of(FunctionGemmaModel.systemPrompt(spec)),
        tools = listOf(tool(TransactionTool)),
        samplerConfig = GREEDY,
        automaticToolCalling = false,   // propose only: the user confirms, the app executes
        maxOutputToken = MAX_OUTPUT_TOKENS,
    )

    private companion object {
        private const val TAG = "FunctionGemma"
        private val LOAD_WAIT = 15.seconds

        /** A full call is ≈ 50 tokens; notebook 02 evaluates with the same bound (MAX_NEW_TOKENS). */
        private const val MAX_OUTPUT_TOKENS = 96

        /** Deterministic: always the most likely token. */
        private val GREEDY = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0)
    }
}
