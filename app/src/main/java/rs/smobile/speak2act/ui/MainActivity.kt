package rs.smobile.speak2act.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import rs.smobile.speak2act.R
import rs.smobile.speak2act.ui.theme.BackgroundDarkBottom
import rs.smobile.speak2act.ui.theme.BackgroundDarkTop
import rs.smobile.speak2act.ui.theme.Speak2ActTheme
import rs.smobile.speak2act.viewmodel.RecorderViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: RecorderViewModel = hiltViewModel()
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
            val amplitudes by mainViewModel.amplitudes.collectAsStateWithLifecycle()
            val isRecording by mainViewModel.isRecording.collectAsStateWithLifecycle()

            val backgroundGradient = Brush.linearGradient(
                colors = listOf(
                    BackgroundDarkTop,
                    BackgroundDarkBottom
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            )

            Speak2ActTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundGradient),
                ) {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        text = stringResource(R.string.voice_intent_action),
                                        modifier = Modifier.padding(vertical = 24.dp),
                                        style = MaterialTheme.typography.headlineLarge
                                    )
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent
                                )
                            )
                        },
                        containerColor = Color.Transparent
                    ) { innerPadding ->
                        VoiceRecorderScreen(
                            innerPadding = innerPadding,
                            uiState = uiState,
                            isRecording = isRecording,
                            amplitudes = amplitudes,
                            onRecordClick = {
                                if (isRecording) {
                                    mainViewModel.stopRecording()
                                } else {
                                    mainViewModel.startRecording()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}