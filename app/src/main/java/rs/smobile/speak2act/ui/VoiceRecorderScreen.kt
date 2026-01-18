package rs.smobile.speak2act.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import rs.smobile.speak2act.R
import rs.smobile.speak2act.RecorderViewModel
import rs.smobile.speak2act.audio.AudioRecorder
import rs.smobile.speak2act.ui.theme.Speak2ActTheme
import java.io.File
import java.util.Locale

@Composable
fun VoiceRecorderScreen(
    innerPadding: PaddingValues
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(getAudioPermissionState(context)) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            permissionGranted = result
        }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (!permissionGranted) {
        Text(stringResource(R.string.microphone_permission_is_required))
    } else {
        RecorderContent(context, innerPadding)
    }
}

@Composable
private fun RecorderContent(
    context: Context,
    innerPadding: PaddingValues,
) {
    val mainViewModel: RecorderViewModel = viewModel<RecorderViewModel>()
    val instruction: String? by mainViewModel.result.collectAsStateWithLifecycle()

    val recorder = remember { AudioRecorder(context) }

    var isRecording by remember { mutableStateOf(false) }
    val amplitudes = remember { mutableStateListOf<Int>() }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    val timerSeconds = rememberRecordingTimer(isRecording)

    var recordingSession by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                val amp = recorder.getAmplitude()
                amplitudes.add(amp)
                delay(100)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp)
            .padding(innerPadding)
    ) {
        val horizontalPaddingModifier = Modifier.padding(horizontal = 24.dp)

        Button(
            onClick = {
                if (isRecording) {
                    recorder.stop()
                    mainViewModel.sendPrompt(recordedFile!!.readBytes())
                    isRecording = false
                } else {
                    amplitudes.clear()
                    recordingSession++
                    recordedFile = recorder.start()
                    isRecording = true
                }
            },
            modifier = horizontalPaddingModifier,
            enabled = instruction?.isNotEmpty() ?: true,
            shape = RoundedCornerShape(8.dp)
        ) {
            val resId =
                if (isRecording) R.string.stop_audio_recording else R.string.start_audio_recording
            Text(text = stringResource(resId))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Waveform(
            amplitudes = amplitudes,
            recordingSession = recordingSession
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = String.format(
                Locale.US, "Recording: %02d:%02d", timerSeconds / 60, timerSeconds % 60
            ), modifier = horizontalPaddingModifier, fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!isRecording) {
            instruction?.apply {
                if (isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        text = this,
                        modifier = horizontalPaddingModifier,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
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

private fun getAudioPermissionState(context: Context): Boolean {
    val permission = Manifest.permission.RECORD_AUDIO
    val granted = ContextCompat.checkSelfPermission(context, permission) == PERMISSION_GRANTED
    return granted
}

@Preview(showBackground = true)
@Composable
private fun VoiceRecorderScreenPreview() {
    Speak2ActTheme {
        VoiceRecorderScreen(PaddingValues(24.dp))
    }
}