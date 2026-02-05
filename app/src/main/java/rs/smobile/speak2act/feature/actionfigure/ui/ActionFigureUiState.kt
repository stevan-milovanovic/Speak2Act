package rs.smobile.speak2act.feature.actionfigure.ui

import androidx.annotation.StringRes
import rs.smobile.speak2act.R

sealed class ActionFigureUiState(@StringRes titleResId: Int) {
    object PickImage : ActionFigureUiState(R.string.pick_image)
    object UploadImage : ActionFigureUiState(R.string.upload_image)
    object ActionFigureImage : ActionFigureUiState(R.string.generated_action_figure)
}