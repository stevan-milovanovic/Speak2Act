package rs.smobile.speak2act.feature.actionfigure.data

import rs.smobile.speak2act.feature.actionfigure.domain.ActionFigureModelService
import javax.inject.Inject

class MeshyActionFigureModelService @Inject constructor(
    val meshyApi: MeshyApi
) : ActionFigureModelService {
    override suspend fun generateActionFigure3DModel(imageUrl: String): Result<String> {
        val response = meshyApi.generateModel(GenerateRequest(imageUrl))
        return Result.success(response.result)
    }

    override suspend fun fetchActionFigure3DModelTaskStatus(taskId: String): Result<TaskResponse> {
        val response = meshyApi.checkTask(taskId)
        return Result.success(response)
    }

    override suspend fun downloadActionFigureModel(url: String): Result<ByteArray> {
        val response = meshyApi.downloadModel(url)
        val unexpectedException = Exception("Unsuccessful download action figure model request!")
        val failure = Result.failure<ByteArray>(unexpectedException)
        return if (response.isSuccessful) {
            response.body()?.let { Result.success(it.bytes()) } ?: failure
        } else {
            failure
        }
    }
}