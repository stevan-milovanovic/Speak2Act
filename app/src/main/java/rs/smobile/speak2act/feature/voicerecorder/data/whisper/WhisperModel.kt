package rs.smobile.speak2act.feature.voicerecorder.data.whisper

/**
 * Descriptor for the on-demand Whisper model.
 *
 * The model is intentionally **not** bundled in the APK (that would add ~74 MB for a feature
 * most users never touch). Instead the quantized `tiny-q8_0` model (~42 MB) is downloaded
 * into internal storage on first use and cached — see [WhisperModelDownloader].
 *
 * [URL] points at the public whisper.cpp model repository. [SIZE_BYTES] and [SHA_256] pin the
 * expected artifact so a corrupt or tampered download is rejected.
 */
object WhisperModel {
    const val FILE_NAME = "ggml-tiny-q8_0.bin"
    const val URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q8_0.bin"
    const val SIZE_BYTES = 43_537_433L
    const val SHA_256 = "c2085835d3f50733e2ff6e4b41ae8a2b8d8110461e18821b09a15c40c42d1cca"
}
