package rs.smobile.speak2act.feature.actionfigure.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import rs.smobile.speak2act.feature.actionfigure.ActionFigureConstants
import rs.smobile.speak2act.feature.actionfigure.domain.ActionFigureService
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class ReplicateActionFigureService @Inject constructor(
    private val apiKey: String
) : ActionFigureService {

    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .build()

    override suspend fun generateActionFigure(imageUrl: String, prompt: String?): Result<String> =
        withContext(
            Dispatchers.IO
        ) {
            try {
                val bodyJson = JSONObject().apply {
                    put("input", JSONObject().apply {
                        put("prompt", prompt ?: DEFAULT_PROMPT)
                        put("resolution", "1 MP")
                        put("aspect_ratio", "9:16")
                        put("input_images", JSONArray().apply { put(imageUrl) })
                        put("output_format", "png")
                        put("output_quality", 80)
                        put("safety_tolerance", 2)
                        put("prompt_upsampling", false)
                    })
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = bodyJson.toString().toRequestBody(mediaType)

                val baseUrl = ActionFigureConstants.REPLICATE_API_BASE_URL
                val modelVendor = "black-forest-labs"
                val model = "flux-2-pro"
                val request = Request.Builder()
                    .url("$baseUrl/models/$modelVendor/$model/predictions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "wait")
                    .post(requestBody).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("${response.code}: ${response.message}"))
                    }
                    val respBody = response.body.string()
                    val json = JSONObject(respBody)
                    val output = when {
                        json.has("output") -> {
                            when (val out = json.get("output")) {
                                is JSONArray -> out.optString(0, "")
                                else -> out.toString()
                            }
                        }

                        else -> ""
                    }

                    if (output.isBlank()) {
                        Result.failure(Exception("No output URL in response: $respBody"))
                    } else {
                        Result.success(output)
                    }
                }
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
}

private const val DEFAULT_PROMPT =
    "Create a highly detailed action figure based on the provided character. " +
            "Remove a background from the original image and keep only the person. " +
            "The action figure should preserve the character’s facial features, hairstyle, and overall proportions. " +
            "Use a realistic action figure aesthetic which has a premium plastic finish with subtle surface texture. " +
            "Action figure should wear futuristic armor, cyberpunk elements, high-tech aesthetic. " +
            "Style should be studio lighting, neutral background, high realism, product photography look, 4k detail. " +
            "The final result should look like a collectible action figure, not a real human."