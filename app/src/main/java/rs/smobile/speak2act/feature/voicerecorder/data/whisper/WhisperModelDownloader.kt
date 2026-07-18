package rs.smobile.speak2act.feature.voicerecorder.data.whisper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Ensures the quantized Whisper model is available in internal storage, downloading it once
 * on first use and caching it for subsequent runs.
 *
 * The download is verified against the pinned size and SHA-256 in [WhisperModel] before it is
 * accepted, and written to a temporary file first so an interrupted download can never leave a
 * partial model in place.
 */
class WhisperModelDownloader(
    private val filesDir: File,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {

    /**
     * Returns the model file, downloading and verifying it if it is not already cached.
     * [onProgress] receives a value in `[0, 1]`. Heavy IO, so it runs on [Dispatchers.IO].
     */
    suspend fun ensureModel(onProgress: (Float) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            val modelFile = File(filesDir, WhisperModel.FILE_NAME)
            deleteStaleModels(keep = modelFile)
            if (isValid(modelFile)) {
                Log.d(TAG, "Whisper model already cached (${modelFile.length()} bytes)")
                onProgress(1f)
                return@withContext modelFile
            }
            download(modelFile, onProgress)
            modelFile
        }

    /** Removes previously downloaded model files (e.g. an older quantization) to reclaim space. */
    private fun deleteStaleModels(keep: File) {
        filesDir.listFiles { file ->
            file.isFile &&
                file.name.startsWith("ggml-") &&
                file.name.endsWith(".bin") &&
                file.name != keep.name
        }?.forEach { stale ->
            if (stale.delete()) {
                Log.d(TAG, "Removed stale Whisper model ${stale.name}")
            }
        }
    }

    private fun isValid(file: File): Boolean =
        file.exists() &&
            file.length() == WhisperModel.SIZE_BYTES &&
            sha256(file) == WhisperModel.SHA_256

    private suspend fun download(target: File, onProgress: (Float) -> Unit) {
        Log.d(TAG, "Downloading Whisper model from ${WhisperModel.URL}")
        val tempFile = File(filesDir, "${WhisperModel.FILE_NAME}.tmp")
        tempFile.delete()

        val request = Request.Builder().url(WhisperModel.URL).build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body
            check(response.isSuccessful) {
                "Model download failed: HTTP ${response.code}"
            }
            val total = body.contentLength().takeIf { it > 0 } ?: WhisperModel.SIZE_BYTES
            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastLoggedDecile = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val progress = (downloaded.toFloat() / total).coerceIn(0f, 1f)
                        onProgress(progress)
                        val decile = (progress * 10).toInt()
                        if (decile != lastLoggedDecile) {
                            Log.d(TAG, "Whisper model download: ${decile * 10}%")
                            lastLoggedDecile = decile
                        }
                    }
                }
            }
        }

        val actualSha = sha256(tempFile)
        if (tempFile.length() != WhisperModel.SIZE_BYTES || actualSha != WhisperModel.SHA_256) {
            tempFile.delete()
            error("Model verification failed (size=${tempFile.length()}, sha256=$actualSha)")
        }
        if (!tempFile.renameTo(target)) {
            tempFile.copyTo(target, overwrite = true)
            tempFile.delete()
        }
        Log.d(TAG, "Whisper model ready at ${target.absolutePath}")
    }

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
        private const val TAG = "WhisperModelDownloader"
    }
}
