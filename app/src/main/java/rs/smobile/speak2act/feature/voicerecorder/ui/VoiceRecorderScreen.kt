package rs.smobile.speak2act.feature.voicerecorder.ui

import android.Manifest.permission.RECORD_AUDIO
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.checkSelfPermission
import kotlinx.coroutines.delay
import rs.smobile.speak2act.R
import rs.smobile.speak2act.core.theme.BackgroundDarkBottom
import rs.smobile.speak2act.core.theme.BackgroundDarkTop
import rs.smobile.speak2act.core.theme.BrandBlueSoft
import rs.smobile.speak2act.core.theme.Speak2ActTheme
import rs.smobile.speak2act.feature.voicerecorder.domain.Transaction
import rs.smobile.speak2act.feature.voicerecorder.ui.component.DetectionOutcome
import rs.smobile.speak2act.feature.voicerecorder.ui.component.RecordButton
import rs.smobile.speak2act.feature.voicerecorder.ui.component.Waveform

@Composable
fun VoiceRecorderScreen(
    modifier: Modifier = Modifier,
    innerPadding: PaddingValues,
    uiState: VoiceRecorderUiState,
    isRecording: Boolean,
    amplitudes: List<Float>,
    onRecordClick: () -> Unit
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(getAudioPermissionState(context)) }

    val permissionLauncher =
        rememberLauncherForActivityResult(RequestPermission()) { permissionGranted = it }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(RECORD_AUDIO)
        }
    }

    if (!permissionGranted) {
        Text(stringResource(R.string.microphone_permission_is_required))
    } else {
        RecorderContent(modifier, innerPadding, uiState, isRecording, amplitudes, onRecordClick)
    }
}

@Composable
private fun RecorderContent(
    modifier: Modifier,
    innerPadding: PaddingValues,
    uiState: VoiceRecorderUiState,
    isRecording: Boolean,
    amplitudes: List<Float>,
    onRecordClick: () -> Unit
) {
    val timerSeconds = rememberRecordingTimer(isRecording)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 24.dp)
            .padding(innerPadding)
    ) {
        val horizontalPaddingModifier = Modifier.padding(horizontal = 24.dp)
        val size = 140.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(size), contentAlignment = Alignment.Center
        ) {
            HorizontalDivider(
                modifier = Modifier.alpha(0.15f), thickness = 2.dp, color = BrandBlueSoft
            )
            Waveform(
                amplitudes = amplitudes,
            )
            RecordButton(
                modifier = Modifier.size(size),
                isRecording = isRecording,
                enabled = uiState !is VoiceRecorderUiState.Loading,
                onRecordButtonTap = onRecordClick
            )
        }

        if (isRecording) {
            Spacer(modifier = Modifier.height(16.dp))
            val mins = timerSeconds / 60
            val secs = timerSeconds % 60
            Text(
                text = stringResource(R.string.time, mins, secs),
                modifier = horizontalPaddingModifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.recording),
                modifier = horizontalPaddingModifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        DetectionOutcome(horizontalPaddingModifier, uiState)

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Powered by Firebase AI Logic + Gemini",
            modifier = horizontalPaddingModifier.fillMaxWidth(),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun rememberRecordingTimer(isRecording: Boolean): Int {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            seconds = 0
            while (true) {
                delay(1_000)
                seconds++
            }
        }
    }
    return seconds
}

private fun getAudioPermissionState(context: Context): Boolean =
    checkSelfPermission(context, RECORD_AUDIO) == PERMISSION_GRANTED

@Preview(showBackground = true)
@Composable
private fun LoadingPreview() {
    Speak2ActTheme {
        val backgroundGradient = Brush.linearGradient(
            colors = listOf(BackgroundDarkTop, BackgroundDarkBottom),
            start = Offset.Zero,
            end = Offset.Infinite
        )
        VoiceRecorderScreen(
            modifier = Modifier.background(backgroundGradient),
            innerPadding = PaddingValues(0.dp),
            uiState = VoiceRecorderUiState.Loading,
            isRecording = false,
            amplitudes = emptyList(),
            onRecordClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorPreview() {
    Speak2ActTheme {
        val backgroundGradient = Brush.linearGradient(
            colors = listOf(BackgroundDarkTop, BackgroundDarkBottom),
            start = Offset.Zero,
            end = Offset.Infinite
        )
        VoiceRecorderScreen(
            modifier = Modifier.background(backgroundGradient),
            innerPadding = PaddingValues(0.dp),
            uiState = VoiceRecorderUiState.Error("Send Maria 50 euros for the dinner"),
            isRecording = false,
            amplitudes = emptyList(),
            onRecordClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SuccessPreview() {
    Speak2ActTheme {
        val backgroundGradient = Brush.linearGradient(
            colors = listOf(BackgroundDarkTop, BackgroundDarkBottom),
            start = Offset.Zero,
            end = Offset.Infinite
        )
        VoiceRecorderScreen(
            modifier = Modifier.background(backgroundGradient),
            innerPadding = PaddingValues(0.dp),
            uiState = VoiceRecorderUiState.Success(
                Transaction(
                    action = "Send",
                    amount = 50.00,
                    currency = "euros",
                    person = "Maria",
                    description = "Dinner"
                )
            ),
            isRecording = false,
            amplitudes = emptyList(),
            onRecordClick = {})
    }
}
