package rs.smobile.speak2act.feature.actionfigure.domain

import rs.smobile.speak2act.feature.actionfigure.data.TaskResponse

interface ActionFigureModelService {

    /**
     * @param imageUrl url of the action figure image based on which model is created
     * @return task ID of action figure model generation task
     */
    suspend fun generateActionFigure3DModel(
        imageUrl: String
    ): Result<String>

    /**
     * @param taskId for the created action figure generation task
     * @return task response in which status and model url are stated
     */
    suspend fun fetchActionFigure3DModelTaskStatus(
        taskId: String
    ): Result<TaskResponse>

    /**
     * @param url for the action figure 3D model in glb format
     * @return byte array of the downloaded model
     */
    suspend fun downloadActionFigureModel(
        url: String
    ): Result<ByteArray>

}