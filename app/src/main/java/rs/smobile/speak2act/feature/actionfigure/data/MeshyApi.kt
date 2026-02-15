package rs.smobile.speak2act.feature.actionfigure.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url


interface MeshyApi {
    @POST("image-to-3d")
    suspend fun generateModel(
        @Body request: GenerateRequest
    ): GenerateResponse

    @GET("image-to-3d/{id}")
    suspend fun checkTask(
        @Path("id") taskId: String
    ): TaskResponse

    @GET
    suspend fun downloadModel(
        @Url url: String
    ): Response<ResponseBody>
}

@Serializable
data class GenerateRequest(
    @SerialName("image_url") val imageUrl: String
)

@Serializable
data class GenerateResponse(
    val result: String
)

@Serializable
data class TaskResponse(
    val status: TaskStatus,
    val progress: Int,
    @SerialName("model_url") val modelUrl: String?
)

@Serializable
enum class TaskStatus {
    @SerialName("PENDING")
    PENDING,

    @SerialName("IN_PROGRESS")
    IN_PROGRESS,

    @SerialName("SUCCEEDED")
    SUCCEEDED,

    @SerialName("FAILED")
    FAILED,

    @SerialName("CANCELED")
    CANCELED
}