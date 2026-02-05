package rs.smobile.speak2act.feature.actionfigure.data

import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.cloudinary.android.policy.TimeWindow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import rs.smobile.speak2act.feature.actionfigure.domain.ImageUploadService
import javax.inject.Inject


class CloudinaryImageUploadService @Inject constructor() : ImageUploadService {
    override fun uploadImage(uri: Uri): Flow<ImageUploadState> = callbackFlow {
        val uploadCallback = object : UploadCallback {
            override fun onStart(requestId: String?) {
                trySend(ImageUploadState.Started(uri))
            }

            override fun onProgress(
                requestId: String?,
                bytes: Long,
                totalBytes: Long
            ) {
                val percent = ((bytes * 100) / (totalBytes + 1)).toInt()
                trySend(ImageUploadState.Progress(uri, percent))
            }

            override fun onSuccess(
                requestId: String?,
                resultData: Map<*, *>?
            ) {
                val url = resultData?.get("secure_url") as? String
                if (url != null) {
                    trySend(ImageUploadState.Success(uri, url))
                } else {
                    trySend(ImageUploadState.Error(uri, "Missing secure url"))
                }
                close()
            }

            override fun onError(
                requestId: String?,
                error: ErrorInfo?
            ) {
                trySend(ImageUploadState.Error(uri, error?.description))
                close()
            }

            override fun onReschedule(
                requestId: String?,
                error: ErrorInfo?
            ) {
                trySend(ImageUploadState.Error(uri, error?.description))
            }
        }

        val requestId = MediaManager.get().upload(uri)
            .unsigned("sample_app_preset")
            .constrain(TimeWindow.immediate())
            .callback(uploadCallback)
            .dispatch()

        awaitClose { MediaManager.get().cancelRequest(requestId) }
    }
}