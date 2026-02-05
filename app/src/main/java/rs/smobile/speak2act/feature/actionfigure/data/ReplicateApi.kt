package rs.smobile.speak2act.feature.actionfigure.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface ReplicateApi {
    @POST("models/{vendor}/{model}/predictions")
    suspend fun createPrediction(
        @Path("vendor") vendor: String,
        @Path("model") model: String,
        @Body request: ReplicateRequest
    ): ReplicateResponse
}

@Serializable
data class ReplicateRequest(
    val input: ReplicateInput
)

@Serializable
data class ReplicateInput(
    val prompt: String,
    val resolution: String? = null,
    @SerialName("aspect_ratio") val aspectRatio: String? = null,
    @SerialName("input_images") val inputImages: List<String>? = null,
    @SerialName("output_format") val outputFormat: String? = null,
    @SerialName("output_quality") val outputQuality: Int? = null,
    @SerialName("safety_tolerance") val safetyTolerance: Int? = null,
    @SerialName("prompt_upsampling") val promptUpsampling: Boolean? = null
)

@Serializable
data class ReplicateResponse(
    val output: JsonElement? = null
)
