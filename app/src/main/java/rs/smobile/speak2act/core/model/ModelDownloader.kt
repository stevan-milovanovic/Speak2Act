package rs.smobile.speak2act.core.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import rs.smobile.speak2act.BuildConfig
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.time.TimeSource

/**
 * Ensures the pinned model file described by [spec] is available in [dir], downloading it once on first use and
 * caching it for subsequent runs.
 *
 * A download is written to a `.tmp` file and verified against the pinned size and SHA-256 before it is renamed into
 * place, so an interrupted, cancelled or tampered download never leaves a partial model behind. After a successful
 * full check a small `<fileName>.verified` marker is written, so later starts don't hash the whole file again.
 *
 * [log] defaults to Logcat; tests pass their own, so they run without `android.util.Log`.
 */
class ModelDownloader(
    private val dir: File,
    private val spec: ModelSpec,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val log: (String) -> Unit = { Log.d(TAG, it) },
) {

    /** Where the model is (or will be) stored. */
    val file: File = File(dir, spec.fileName)
    private val marker = File(dir, spec.fileName + MARKER_SUFFIX)
    private val tempFile = File(dir, spec.fileName + TEMP_SUFFIX)

    /** Cheap check, no hashing: the file exists, its size matches and its verification marker matches. */
    fun isDownloaded(): Boolean =
        spec.isPublished && file.isFile && file.length() == spec.sizeBytes && markerMatches()

    /**
     * Verifies the cached file without the network: `true` for a matching marker. A file of the right size without
     * a marker (a crash between rename and marker, a file copied in by hand) is hashed once and gets its marker; a
     * file that fails the check is deleted. Always `false` for an unpublished spec, whose file is never touched.
     */
    suspend fun verifyCached(): Boolean = withContext(Dispatchers.IO) {
        if (!spec.isPublished) return@withContext false
        deleteStaleFiles()
        if (!file.isFile) return@withContext false
        if (isDownloaded()) return@withContext true
        val mark = TimeSource.Monotonic.markNow()
        val valid = file.length() == spec.sizeBytes && sha256(file) == spec.sha256
        if (BuildConfig.DEBUG) {
            log("sha256_check=${mark.elapsedNow().inWholeMilliseconds}ms valid=$valid model=${spec.fileName}")
        }
        if (valid) {
            writeMarker()
        } else {
            marker.delete()
            file.delete()
        }
        valid
    }

    /**
     * Returns the model file, downloading and verifying it if it is not already cached. [onProgress] receives a
     * value in `[0, 1]`, and only while a download runs: a cached file reports nothing, so the UI never shows a
     * download that isn't happening. Heavy IO, so it runs on [Dispatchers.IO]. Refuses an unpublished spec.
     */
    suspend fun ensureModel(onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        check(spec.isPublished) { "Model ${spec.fileName} is not published" }
        if (verifyCached()) {
            log("${spec.fileName} already cached (${file.length()} bytes)")
            return@withContext file
        }
        download(onProgress)
        file
    }

    /** Removes older files of the same model family (e.g. another quantization) and their markers. */
    private fun deleteStaleFiles() {
        dir.listFiles { candidate ->
            val name = candidate.name.removeSuffix(MARKER_SUFFIX)
            candidate.isFile && name != spec.fileName && spec.isStale(name)
        }?.forEach { stale ->
            if (stale.delete()) log("Removed stale model file ${stale.name}")
        }
    }

    private suspend fun download(onProgress: (Float) -> Unit) {
        log("Downloading ${spec.fileName} from ${spec.url}")
        dir.mkdirs()
        marker.delete()
        tempFile.delete()
        try {
            val request = Request.Builder().url(spec.url).build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Model download failed: HTTP ${response.code}" }
                val body = response.body
                val contentLength = body.contentLength()
                // Refused before copying: a broken or hostile response can't fill the storage.
                check(contentLength < 0 || contentLength == spec.sizeBytes) {
                    "Model download refused: Content-Length $contentLength, expected ${spec.sizeBytes}"
                }
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output -> copy(input, output, onProgress) }
                }
            }
            val actualSha = sha256(tempFile)
            check(tempFile.length() == spec.sizeBytes && actualSha == spec.sha256) {
                "Model verification failed (size=${tempFile.length()}, sha256=$actualSha)"
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
            }
            writeMarker()
            log("${spec.fileName} ready at ${file.absolutePath}")
        } finally {
            // Nothing to delete once renamed; otherwise (failure, cancellation, copy fallback) the partial file goes.
            tempFile.delete()
        }
    }

    /** Copies the body, aborting as soon as it is longer than the pinned size. */
    private suspend fun copy(input: InputStream, output: OutputStream, onProgress: (Float) -> Unit) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var downloaded = 0L
        var lastLoggedDecile = -1
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            downloaded += read
            check(downloaded <= spec.sizeBytes) { "Model download aborted: more than ${spec.sizeBytes} bytes" }
            output.write(buffer, 0, read)
            val progress = (downloaded.toFloat() / spec.sizeBytes).coerceIn(0f, 1f)
            onProgress(progress)
            val decile = (progress * 10).toInt()
            if (decile != lastLoggedDecile) {
                log("${spec.fileName} download: ${decile * 10}%")
                lastLoggedDecile = decile
            }
        }
    }

    private fun markerMatches(): Boolean =
        marker.isFile && runCatching { marker.readText() }.getOrNull() == markerContent()

    private fun writeMarker() = marker.writeText(markerContent())

    /** "<sha256> <length> <lastModified>": a replaced or modified file no longer matches. */
    private fun markerContent(): String = "${spec.sha256} ${file.length()} ${file.lastModified()}"

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        private const val TAG = "ModelDownloader"
        private const val MARKER_SUFFIX = ".verified"
        private const val TEMP_SUFFIX = ".tmp"
    }
}
