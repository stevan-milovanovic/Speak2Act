package rs.smobile.speak2act.feature.actionfigure

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import rs.smobile.speak2act.feature.actionfigure.data.ReplicateActionFigureService
import rs.smobile.speak2act.feature.actionfigure.data.ReplicateApi

class ReplicateActionFigureServiceTest {
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `generateActionFigure parses output url`() = runBlocking {
        val outputUrl = "https://example.com/generated.png"
        val jsonResponse = "{ \"output\": [\"$outputUrl\"] }"

        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val client = OkHttpClient.Builder().build()
        val json = Json { ignoreUnknownKeys = true }
        val converterFactory = json.asConverterFactory("application/json".toMediaType())
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(converterFactory)
            .build()

        val api = retrofit.create(ReplicateApi::class.java)
        val service = ReplicateActionFigureService(api = api)

        val result = service.generateActionFigure("https://test.com/input.png", null)
        assertEquals(true, result.isSuccess)
        assertEquals(outputUrl, result.getOrNull())
    }
}
