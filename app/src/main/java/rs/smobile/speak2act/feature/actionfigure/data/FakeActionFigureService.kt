package rs.smobile.speak2act.feature.actionfigure.data

import rs.smobile.speak2act.BuildConfig
import rs.smobile.speak2act.feature.actionfigure.ActionFigureConstants
import rs.smobile.speak2act.feature.actionfigure.domain.ActionFigureService

class FakeActionFigureService() : ActionFigureService {
    override suspend fun generateActionFigure(
        imageUrl: String,
        prompt: String?
    ): Result<String> {
        val baseUrl = ActionFigureConstants.CLOUDINARY_API_BASE_URL
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        val imageUrl = BuildConfig.ACTION_FIGURE_TEST_IMAGE
        return Result.success("$baseUrl/$cloudName/image/upload/$imageUrl")
    }
}