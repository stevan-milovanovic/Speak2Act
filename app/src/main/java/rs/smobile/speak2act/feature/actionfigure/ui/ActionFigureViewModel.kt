package rs.smobile.speak2act.feature.actionfigure.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rs.smobile.speak2act.feature.actionfigure.data.ImageUploadState
import rs.smobile.speak2act.feature.actionfigure.domain.ActionFigureService
import rs.smobile.speak2act.feature.actionfigure.domain.ImageUploadService
import rs.smobile.speak2act.feature.actionfigure.ui.result.ActionFigureGenerationState
import javax.inject.Inject

@HiltViewModel
class ActionFigureViewModel @Inject constructor(
    private val actionFigureService: ActionFigureService,
    private val imageUploadService: ImageUploadService
) : ViewModel() {

    private val _uiState = MutableStateFlow<ActionFigureUiState>(ActionFigureUiState.PickImage)
    val uiState: StateFlow<ActionFigureUiState> = _uiState

    private val _imageUploadState = MutableStateFlow<ImageUploadState?>(null)
    val imageUploadState: StateFlow<ImageUploadState?> = _imageUploadState

    private val _actionFigureGenerationState = MutableStateFlow<ActionFigureGenerationState>(
        ActionFigureGenerationState.Initial()
    )
    val actionFigureGenerationState: StateFlow<ActionFigureGenerationState> =
        _actionFigureGenerationState

    fun uploadImage(uri: Uri) {
        _uiState.value = ActionFigureUiState.UploadImage
        viewModelScope.launch {
            imageUploadService.uploadImage(uri).collect { state ->
                _imageUploadState.value = state
            }
        }
    }

    fun generate(prompt: String? = null) {
        _uiState.value = ActionFigureUiState.ActionFigureImage
        viewModelScope.launch {
            val url = (imageUploadState.value as? ImageUploadState.Success)?.url

            if (url == null) {
                _actionFigureGenerationState.value =
                    ActionFigureGenerationState.Error(message = "There is no image url for action figure generation")
                return@launch
            }

            _actionFigureGenerationState.value = ActionFigureGenerationState.Loading(url)
            val res = actionFigureService.generateActionFigure(url, prompt)
            if (res.isSuccess) {
                val imageUrl = res.getOrNull()
                if (imageUrl != null) {
                    _actionFigureGenerationState.value =
                        ActionFigureGenerationState.Success(url, imageUrl)
                } else {
                    _actionFigureGenerationState.value = ActionFigureGenerationState.Error(
                        message = res.exceptionOrNull()?.message
                            ?: "Request successful but there is no result image"
                    )
                }
            } else {
                _actionFigureGenerationState.value = ActionFigureGenerationState.Error(
                    message = res.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        }
    }
}
