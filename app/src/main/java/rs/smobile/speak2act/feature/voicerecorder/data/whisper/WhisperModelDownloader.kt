package rs.smobile.speak2act.feature.voicerecorder.data.whisper

import okhttp3.OkHttpClient
import rs.smobile.speak2act.core.model.ModelDownloader
import java.io.File

/**
 * Ensures the quantized Whisper model is available in internal storage, downloading it once
 * on first use and caching it for subsequent runs.
 *
 * Delegates to the generic [ModelDownloader]: the download is verified against the pinned size and
 * SHA-256 in [WhisperModel] before it is accepted, and written to a temporary file first so an
 * interrupted download can never leave a partial model in place.
 */
class WhisperModelDownloader(
    filesDir: File,
    httpClient: OkHttpClient = OkHttpClient(),
) {

    private val downloader = ModelDownloader(filesDir, WhisperModel.spec, httpClient)

    /**
     * Returns the model file, downloading and verifying it if it is not already cached.
     * [onProgress] receives a value in `[0, 1]`. Heavy IO, so it runs on `Dispatchers.IO`.
     */
    suspend fun ensureModel(onProgress: (Float) -> Unit = {}): File = downloader.ensureModel(onProgress)
}
