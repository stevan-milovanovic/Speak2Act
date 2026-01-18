package rs.smobile.speak2act.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import rs.smobile.speak2act.R
import rs.smobile.speak2act.ui.theme.Speak2ActTheme

@Composable
fun RecordButton(
    modifier: Modifier,
    isRecording: Boolean = false,
    enabled: Boolean = true,
    onRecordButtonTap: () -> Unit
) {
    IconButton(
        onClick = onRecordButtonTap,
        modifier = modifier,
        enabled = enabled
    ) {
        Icon(
            painter = painterResource(R.drawable.record),
            modifier = Modifier.alpha(if (isRecording) 0.3f else 1f),
            contentDescription = stringResource(R.string.app_name),
            tint = Color.Unspecified
        )
    }
}

@Preview
@Composable
private fun RecordButtonPreview() {
    Speak2ActTheme {
        RecordButton(
            modifier = Modifier.size(140.dp),
            isRecording = true
        ) { }
    }
}