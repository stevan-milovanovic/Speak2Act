package rs.smobile.speak2act.feature.actionfigure.data

import android.net.Uri

sealed class ImageUploadState(open val uri: Uri) {
    data class Started(override val uri: Uri) : ImageUploadState(uri)
    data class Progress(override val uri: Uri, val percent: Int) : ImageUploadState(uri)
    data class Success(override val uri: Uri, val url: String) : ImageUploadState(uri)
    data class Error(override val uri: Uri, val message: String?) : ImageUploadState(uri)
}