package rs.smobile.speak2act.feature.voicerecorder.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import rs.smobile.speak2act.R
import rs.smobile.speak2act.core.theme.BackgroundDarkTop
import rs.smobile.speak2act.core.theme.Speak2ActTheme
import rs.smobile.speak2act.feature.voicerecorder.domain.OnDeviceModelStatus

/**
 * Talk 3's one-line status of the on-device language model under the record button: "🧠 On-device model ready"
 * (pink), "… · downloading 42%" with a thin progress bar, "… · loading…", "… · connect to Wi-Fi to download" or
 * "… unavailable" (muted). Nothing while [OnDeviceModelStatus.Idle].
 */
@Composable
fun OnDeviceModelStatusLine(status: OnDeviceModelStatus, modifier: Modifier = Modifier) {
    val label = when (status) {
        OnDeviceModelStatus.Idle -> return
        OnDeviceModelStatus.Missing -> stringResource(R.string.on_device_model_missing)
        is OnDeviceModelStatus.Downloading ->
            stringResource(R.string.on_device_model_downloading, (status.progress.coerceIn(0f, 1f) * 100).toInt())
        OnDeviceModelStatus.Loading -> stringResource(R.string.on_device_model_loading)
        OnDeviceModelStatus.Ready -> stringResource(R.string.on_device_model_ready)
        is OnDeviceModelStatus.Unavailable -> stringResource(R.string.on_device_model_unavailable)
    }
    val color = if (status == OnDeviceModelStatus.Ready) {
        OnDeviceLlmPink
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
        if (status is OnDeviceModelStatus.Downloading) {
            LinearProgressIndicator(
                progress = { status.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(2.dp),
                color = OnDeviceLlmPink
            )
        }
    }
}

@Preview
@Composable
private fun OnDeviceModelStatusLinePreview() {
    Speak2ActTheme {
        Column(
            Modifier
                .background(BackgroundDarkTop)
                .padding(vertical = 16.dp, horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OnDeviceModelStatusLine(OnDeviceModelStatus.Missing)
            OnDeviceModelStatusLine(OnDeviceModelStatus.Downloading(0.42f))
            OnDeviceModelStatusLine(OnDeviceModelStatus.Loading)
            OnDeviceModelStatusLine(OnDeviceModelStatus.Ready)
            OnDeviceModelStatusLine(OnDeviceModelStatus.Unavailable("not_published"))
        }
    }
}
