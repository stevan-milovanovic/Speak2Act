package rs.smobile.speak2act.feature.actionfigure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import rs.smobile.speak2act.core.theme.BackgroundDarkBottom
import rs.smobile.speak2act.core.theme.BackgroundDarkTop
import rs.smobile.speak2act.core.theme.Speak2ActTheme
import rs.smobile.speak2act.feature.actionfigure.data.ImageUploadState
import rs.smobile.speak2act.feature.actionfigure.ui.draft.ModelViewerScreen
import rs.smobile.speak2act.feature.actionfigure.ui.result.ActionFigureGenerationState
import rs.smobile.speak2act.feature.actionfigure.ui.result.ActionFigureResultScreen
import rs.smobile.speak2act.feature.actionfigure.ui.upload.UploadAndSelectStyleScreen

@Composable
fun ActionFigureScreen(
    modifier: Modifier = Modifier,
    innerPadding: PaddingValues,
    uiState: ActionFigureUiState,
    imageUploadState: ImageUploadState?,
    actionFigureGenerationState: ActionFigureGenerationState,
    onPickImage: () -> Unit,
    onGenerate: () -> Unit,
    onGenerateModel: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(top = 36.dp)
            .padding(innerPadding)
    ) {
        when (uiState) {
            ActionFigureUiState.PickImage -> PickImageScreen(modifier, onPickImage)
            ActionFigureUiState.UploadImage -> UploadAndSelectStyleScreen(
                state = imageUploadState,
                selectedStyle = null,
                onStyleSelected = {},
                onGenerate = onGenerate
            )

            ActionFigureUiState.ActionFigureImage -> ActionFigureResultScreen(
                state = actionFigureGenerationState,
                onGenerateModel = onGenerateModel
            )

            ActionFigureUiState.ActionFigureModel -> ModelViewerScreen("textured.glb")
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActionFigureScreenPreview() {
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(
            BackgroundDarkTop,
            BackgroundDarkBottom
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )
    Speak2ActTheme {
        ActionFigureScreen(
            modifier = Modifier.background(backgroundGradient),
            innerPadding = PaddingValues(vertical = 24.dp),
            uiState = ActionFigureUiState.PickImage,
            imageUploadState = null,
            actionFigureGenerationState = ActionFigureGenerationState.Initial(),
            onPickImage = {},
            onGenerate = {},
            onGenerateModel = {}
        )
    }
}