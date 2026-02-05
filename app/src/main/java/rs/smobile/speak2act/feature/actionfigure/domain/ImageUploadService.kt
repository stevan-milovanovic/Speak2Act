package rs.smobile.speak2act.feature.actionfigure.domain

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import rs.smobile.speak2act.feature.actionfigure.data.ImageUploadState

/**
 * Uploads provided image to the server and provides publicly accessible url
 */
interface ImageUploadService {

    fun uploadImage(uri: Uri): Flow<ImageUploadState>

}