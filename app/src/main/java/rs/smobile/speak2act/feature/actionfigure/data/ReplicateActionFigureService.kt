package rs.smobile.speak2act.feature.actionfigure.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import rs.smobile.speak2act.feature.actionfigure.domain.ActionFigureService
import javax.inject.Inject


class ReplicateActionFigureService @Inject constructor(
    private val api: ReplicateApi
) : ActionFigureService {

    override suspend fun generateActionFigure(imageUrl: String, prompt: String?): Result<String> =
        try {
            val request = ReplicateRequest(
                input = ReplicateInput(
                    prompt = prompt ?: DEFAULT_PROMPT,
                    resolution = "1 MP",
                    aspectRatio = "9:16",
                    inputImages = listOf(imageUrl),
                    outputFormat = "png",
                    outputQuality = 80,
                    safetyTolerance = 2,
                    promptUpsampling = false
                )
            )

            val modelVendor = "black-forest-labs"
            val model = "flux-2-pro"
            val resp = api.createPrediction(modelVendor, model, request)

            val outputField: JsonElement? = resp.output
            val outputUrl: String = try {
                when (outputField) {
                    null -> ""
                    is JsonArray -> if (outputField.isNotEmpty()) {
                        val first = outputField[0]
                        if (first is JsonPrimitive) first.content else first.toString().trim('"')
                    } else ""

                    is JsonPrimitive -> outputField.content
                    else -> outputField.toString().trim('"')
                }
            } catch (_: Throwable) {
                ""
            }

            if (outputUrl.isBlank()) {
                Result.failure(Exception("No output URL in response: $resp"))
            } else {
                Result.success(outputUrl)
            }
        } catch (t: Throwable) {
            Result.failure(t)
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