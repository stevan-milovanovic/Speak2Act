package rs.smobile.speak2act.feature.actionfigure.ui.upload

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import rs.smobile.speak2act.R
import rs.smobile.speak2act.core.theme.Speak2ActTheme
import rs.smobile.speak2act.feature.actionfigure.data.ImageUploadState
import rs.smobile.speak2act.feature.actionfigure.data.ImageUploadState.Progress
import rs.smobile.speak2act.feature.actionfigure.data.ImageUploadState.Success


@Composable
fun UploadAndSelectStyleScreen(
    state: ImageUploadState?,
    selectedStyle: ActionFigureStyle?,
    onStyleSelected: (ActionFigureStyle) -> Unit,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp)
                .weight(3f),
            contentAlignment = Alignment.Center
        ) {
            val progress = when (state) {
                is Progress -> if (state.percent < 20) 20 else state.percent
                is Success -> 100
                else -> 20
            }
            val model = if (state is Success) state.url else state?.uri
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .aspectRatio(9 / 16f)
                    .alpha(progress / 100f)
                    .background(MaterialTheme.colorScheme.primary),
                contentScale = ContentScale.Crop
            )
            when (state) {
                is Progress -> CircularProgressIndicator(
                    progress = { state.percent / 100f },
                    Modifier
                        .size(160.dp)
                        .alpha((100 - state.percent) / 100f),
                    strokeWidth = 8.dp
                )

                is ImageUploadState.Error -> Text(
                    text = state.message
                        ?: stringResource(R.string.unknown_error_during_image_upload),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 36.dp),
                    style = MaterialTheme.typography.headlineMedium
                )

                else -> Unit
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onGenerate,
            enabled = state is Success,
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.generate_action_figure))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UploadAndSelectStyleScreenPreview() {
    Speak2ActTheme {
        UploadAndSelectStyleScreen(
            state = Progress(Uri.EMPTY, 9),
            selectedStyle = null,
            onStyleSelected = {}
        ) { }
    }
}