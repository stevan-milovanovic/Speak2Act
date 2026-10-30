package rs.smobile.speak2act.core.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelDownloaderTest {

    private companion object {
        private const val FILE_NAME = "model-v2.bin"
        private val CONTENT = ByteArray(100_000) { (it % 251).toByte() }
        private val SHA_256 = sha256(CONTENT)

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var dir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dir = tmp.newFolder("models")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun spec(
        url: String = server.url("/$FILE_NAME").toString(),
        sizeBytes: Long = CONTENT.size.toLong(),
        sha256: String = SHA_256,
    ) = ModelSpec(FILE_NAME, url, sizeBytes, sha256, isStale = { it.startsWith("model-") && it.endsWith(".bin") })

    private fun downloader(spec: ModelSpec = spec()) = ModelDownloader(dir, spec, OkHttpClient(), log = {})

    private fun body(bytes: ByteArray = CONTENT) = MockResponse().setBody(Buffer().write(bytes))

    private val model get() = File(dir, FILE_NAME)
    private val marker get() = File(dir, "$FILE_NAME.verified")
    private val temp get() = File(dir, "$FILE_NAME.tmp")

    @Test
    fun ensureModel_downloadsVerifiesAndRenames_reportingProgressUpToOne() = runTest {
        server.enqueue(body())
        val downloader = downloader()
        val progress = mutableListOf<Float>()
        assertFalse(downloader.isDownloaded())

        val file = downloader.ensureModel { progress += it }

        assertEquals(model, file)
        assertArrayEquals(CONTENT, file.readBytes())
        assertTrue(marker.isFile)
        assertFalse(temp.exists())
        assertEquals(1f, progress.last())
        assertEquals(progress.sorted(), progress)
        assertTrue(downloader.isDownloaded())
    }

    @Test
    fun ensureModel_wrongSha256_leavesNoFileAndNoMarker() = runTest {
        server.enqueue(body())

        val error = runCatching { downloader(spec(sha256 = "0".repeat(64))).ensureModel() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertFalse(model.exists())
        assertFalse(marker.exists())
        assertFalse(temp.exists())
    }

    @Test
    fun ensureModel_validCachedFileWithMarker_makesNoRequest_andReportsNoProgress() = runTest {
        server.enqueue(body())
        downloader().ensureModel()

        val progress = mutableListOf<Float>()
        downloader().ensureModel { progress += it }

        assertEquals(1, server.requestCount)
        assertEquals(emptyList<Float>(), progress)   // nothing downloaded, nothing to show
    }

    @Test
    fun verifyCached_validFileWithoutMarker_isHashedOnce_andGetsAMarker() = runTest {
        model.writeBytes(CONTENT)
        val downloader = downloader()
        assertFalse(downloader.isDownloaded())

        assertTrue(downloader.verifyCached())

        assertEquals(0, server.requestCount)
        assertTrue(marker.isFile)
        assertTrue(downloader.isDownloaded())
    }

    @Test
    fun verifyCached_wrongFile_isDeleted() = runTest {
        model.writeBytes(CONTENT.copyOf().also { it[0] = 42 })   // right size, wrong content
        assertFalse(downloader().verifyCached())
        assertFalse(model.exists())

        model.writeBytes(CONTENT.copyOf(10))                      // wrong size
        assertFalse(downloader().verifyCached())
        assertFalse(model.exists())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun verifyCached_modifiedFile_noLongerMatchesItsMarker() = runTest {
        model.writeBytes(CONTENT)
        assertTrue(downloader().verifyCached())

        model.writeBytes(CONTENT.copyOf().also { it[0] = 42 })
        model.setLastModified(model.lastModified() + 2_000)

        assertFalse(downloader().isDownloaded())
        assertFalse(downloader().verifyCached())
        assertFalse(model.exists())
    }

    @Test
    fun ensureModel_differentContentLength_isRefused() = runTest {
        server.enqueue(body(CONTENT + byteArrayOf(1)))

        val error = runCatching { downloader().ensureModel() }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("Content-Length"))
        assertFalse(temp.exists())
        assertFalse(model.exists())
    }

    @Test
    fun ensureModel_bodyLongerThanPinnedSize_isAborted() = runTest {
        // Chunked: no Content-Length, so only the streaming check can stop it.
        server.enqueue(MockResponse().setChunkedBody(Buffer().write(CONTENT + ByteArray(10_000)), 4_096))

        val error = runCatching { downloader().ensureModel() }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("aborted"))
        assertFalse(temp.exists())
        assertFalse(model.exists())
    }

    @Test
    fun ensureModel_cancelledMidDownload_leavesNoTmp() = runTest {
        server.enqueue(body().throttleBody(1_024, 100, TimeUnit.MILLISECONDS))
        val started = CompletableDeferred<Unit>()

        val job = launch { downloader().ensureModel { started.complete(Unit) } }
        started.await()
        job.cancelAndJoin()

        assertFalse(temp.exists())
        assertFalse(model.exists())
        assertFalse(marker.exists())
    }

    @Test
    fun staleFilesAndMarkers_areDeleted() = runTest {
        val staleModel = File(dir, "model-v1.bin").apply { writeBytes(CONTENT) }
        val staleMarker = File(dir, "model-v1.bin.verified").apply { writeText("x") }
        val unrelated = File(dir, "other.bin").apply { writeBytes(CONTENT) }
        model.writeBytes(CONTENT)

        assertTrue(downloader().verifyCached())

        assertFalse(staleModel.exists())
        assertFalse(staleMarker.exists())
        assertTrue(unrelated.exists())
        assertTrue(model.exists())
    }

    @Test
    fun unpublishedSpec_isRefused_andItsFileIsNeverTouched() = runTest {
        val unpublished = spec(url = "")
        model.writeBytes(byteArrayOf(1, 2, 3))   // e.g. a debug file pushed with adb

        assertFalse(unpublished.isPublished)
        assertFalse(downloader(unpublished).verifyCached())
        assertFalse(downloader(unpublished).isDownloaded())
        assertTrue(runCatching { downloader(unpublished).ensureModel() }.exceptionOrNull() is IllegalStateException)
        assertTrue(model.exists())
        assertEquals(0, server.requestCount)
    }
}
