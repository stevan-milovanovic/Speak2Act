package rs.smobile.speak2act.feature.actionfigure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rs.smobile.speak2act.feature.actionfigure.data.TaskStatus
import rs.smobile.speak2act.feature.actionfigure.domain.ActionFigureModelService
import javax.inject.Inject

@HiltViewModel
class ActionFigure3DViewModel @Inject constructor(
    private val actionFigureModelService: ActionFigureModelService
) : ViewModel() {

    private val _uiState = MutableStateFlow<ByteArray?>(null)
    val uiState: StateFlow<ByteArray?> = _uiState

    private val _progressState = MutableStateFlow(0)
    val progressState: StateFlow<Int> = _progressState

    fun create3DModel(imageUrl: String) {
        viewModelScope.launch {
            val response = actionFigureModelService.generateActionFigure3DModel(imageUrl)
            response.getOrNull()?.let { taskId ->
                val modelDownloadUrl = pollTask(taskId)
                val download = actionFigureModelService.downloadActionFigureModel(modelDownloadUrl)
                download.getOrNull()?.let { _uiState.value = it }
            }
        }
    }

    fun createTextured3DModel(imageUrl: String) {
        viewModelScope.launch {
            val response = actionFigureModelService.generateActionFigure3DModel(imageUrl)
            response.getOrNull()?.let { taskId ->
                val modelDownloadUrl = pollTask(taskId)
                val download = actionFigureModelService.downloadActionFigureModel(modelDownloadUrl)
                download.getOrNull()?.let { _uiState.value = it }
            }
        }
    }

    private suspend fun pollTask(taskId: String): String {
        while (true) {
            val response = actionFigureModelService.fetchActionFigure3DModelTaskStatus(taskId)
            val taskResponse = response.getOrNull()
            taskResponse?.progress?.let {
                _progressState.value = it
            }
            when (taskResponse?.status) {
                TaskStatus.SUCCEEDED ->
                    return taskResponse.modelUrl ?: error("Generation failed")

                TaskStatus.FAILED,
                TaskStatus.CANCELED ->
                    error("Generation failed")

                else -> delay(2000)
            }
        }
    }

}