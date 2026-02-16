package rs.smobile.speak2act.feature.actionfigure.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import rs.smobile.speak2act.feature.actionfigure.domain.ActionFigureModelService
import javax.inject.Inject

class FakeActionFigureModelService @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ActionFigureModelService {

    private var fetchActionCallCount = 0

    override suspend fun generateActionFigure3DModel(imageUrl: String): Result<String> {
        return Result.success("taskId")
    }

    override suspend fun fetchActionFigure3DModelTaskStatus(taskId: String): Result<TaskResponse> {
        fetchActionCallCount++

        return if (fetchActionCallCount <= 10) {
            Result.success(
                TaskResponse(TaskStatus.IN_PROGRESS, fetchActionCallCount * 10, null)
            )
        } else {
            Result.success(
                TaskResponse(TaskStatus.SUCCEEDED, 100, "modelUrl")
            )
        }
    }

    private var downloadActionCallCount = 0

    override suspend fun downloadActionFigureModel(url: String): Result<ByteArray> {
        downloadActionCallCount++
        val fileName = if (downloadActionCallCount == 1) "sample.glb" else "textured.glb"
        val byteArray = context.assets.open(fileName).use { inputStream ->
            inputStream.readBytes()
        }

        return Result.success(byteArray)
    }
}