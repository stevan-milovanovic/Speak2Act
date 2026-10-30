package rs.smobile.speak2act.core.model

/** A model file downloaded on first use and pinned by size and SHA-256. */
data class ModelSpec(
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    /** Older files of the same model family to delete, e.g. another quantization. */
    val isStale: (fileName: String) -> Boolean = { false },
) {
    /** Whether the file has a public download URL. An unpublished spec is never downloaded or verified. */
    val isPublished: Boolean get() = url.isNotBlank()
}
