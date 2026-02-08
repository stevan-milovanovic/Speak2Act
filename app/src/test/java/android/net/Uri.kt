package android.net

/**
 * Minimal shim of android.net.Uri for JVM unit tests.
 * This avoids the "Method parse in android.net.Uri not mocked" error by
 * providing a tiny, test-only implementation used during `test` runs.
 */
data class Uri(private val uriString: String) {
    companion object {
        fun parse(uri: String): Uri = Uri(uri)
    }

    override fun toString(): String = uriString
}
