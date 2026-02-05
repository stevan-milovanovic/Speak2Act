package rs.smobile.speak2act.feature.actionfigure.ui.result

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import rs.smobile.speak2act.R
import rs.smobile.speak2act.core.theme.Speak2ActTheme
import rs.smobile.speak2act.feature.actionfigure.ui.result.ActionFigureGenerationState.Error
import rs.smobile.speak2act.feature.actionfigure.ui.result.ActionFigureGenerationState.Success

private const val ANIMATION_DURATION = 20_000

@Composable
fun ActionFigureResultScreen(
    state: ActionFigureGenerationState,
    onShareResults: () -> Unit = {}
) {
    val isSuccess by remember(state) { derivedStateOf { state is Success } }
    val alpha = remember { Animatable(if (state is Success) 0f else 1f) }
    val scale = remember { Animatable(if (state is Success) 1.1f else 1f) }

    LaunchedEffect(isSuccess) {
        launch {
            alpha.animateTo(
                targetValue = if (isSuccess) 1f else 0f,
                animationSpec = tween(durationMillis = ANIMATION_DURATION)
            )
        }
        launch {
            scale.animateTo(
                targetValue = if (isSuccess) 1f else 1.1f,
                animationSpec = tween(durationMillis = ANIMATION_DURATION)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp)
                .weight(3f),
            contentAlignment = Alignment.Center
        ) {
            if (state is Error) {
                Text(state.message)
                return
            }

            val url = with(state) { if (this is Success) imageUrl else url }

            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.generated_action_figure),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9 / 16f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .graphicsLayer {
                        this.alpha = alpha.value
                        scaleX = scale.value
                        scaleY = scale.value
                    },
                contentScale = ContentScale.Crop
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onShareResults,
            enabled = state is Success,
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.share_results))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionFigureResultScreenPreview() {
    Speak2ActTheme {
        ActionFigureResultScreen(
            state = Success(url = null, "dsa")
        )
    }
}