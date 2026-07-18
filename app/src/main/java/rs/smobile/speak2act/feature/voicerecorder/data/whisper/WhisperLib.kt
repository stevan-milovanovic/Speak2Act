package rs.smobile.speak2act.feature.voicerecorder.data.whisper

/**
 * Thin JNI bridge over the vendored whisper.cpp library (see app/src/main/cpp).
 *
 * The native symbol names are derived from this exact package + class name, so they
 * must stay in sync with the implementations in `whisper_jni.cpp`.
 */
internal class WhisperLib {

    companion object {

        init {
            System.loadLibrary("whisper_jni")
        }

        /** Initializes a whisper context from a model file on disk. Returns 0 on failure. */
        external fun initContext(modelPath: String): Long

        /** Releases a previously created whisper context. */
        external fun freeContext(contextPtr: Long)

        /**
         * Runs a full transcription pass over 16 kHz mono float audio.
         * @param language ISO code (e.g. "en") or "auto" for on-device language detection.
         * @return 0 on success, non-zero on failure.
         */
        external fun fullTranscribe(
            contextPtr: Long,
            numThreads: Int,
            audioData: FloatArray,
            language: String,
        ): Int

        external fun getTextSegmentCount(contextPtr: Long): Int

        external fun getTextSegment(contextPtr: Long, index: Int): String

        external fun getSystemInfo(): String
    }
}
