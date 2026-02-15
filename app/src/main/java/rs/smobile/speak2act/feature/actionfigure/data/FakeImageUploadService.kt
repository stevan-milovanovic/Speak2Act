package rs.smobile.speak2act.feature.actionfigure.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import rs.smobile.speak2act.BuildConfig
import rs.smobile.speak2act.feature.actionfigure.ActionFigureConstants
import rs.smobile.speak2act.feature.actionfigure.domain.ImageUploadService

class FakeImageUploadService : ImageUploadService {
    override fun uploadImage(uri: Uri): Flow<ImageUploadState> = flow {
        emit(ImageUploadState.Started(uri))
        val steps = listOf(10, 25, 45, 70, 90)
        for (p in steps) {
            emit(ImageUploadState.Progress(uri, p))
        }
        val baseUrl = ActionFigureConstants.CLOUDINARY_API_BASE_URL
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        val imageUrl = BuildConfig.UPLOAD_TEST_IMAGE
        emit(
            ImageUploadState.Success(
                uri = uri,
                url = "$baseUrl/$cloudName/image/upload/$imageUrl"
            )
        )
    }
}
