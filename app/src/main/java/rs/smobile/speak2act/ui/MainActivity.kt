package rs.smobile.speak2act.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.AndroidEntryPoint
import rs.smobile.speak2act.R
import rs.smobile.speak2act.ui.billanalyzer.BillScreen
import rs.smobile.speak2act.ui.intro.ActionSelectionScreen
import rs.smobile.speak2act.ui.theme.BackgroundDarkBottom
import rs.smobile.speak2act.ui.theme.BackgroundDarkTop
import rs.smobile.speak2act.ui.theme.Speak2ActTheme
import rs.smobile.speak2act.ui.voicerecorder.VoiceRecorderScreen
import rs.smobile.speak2act.viewmodel.BillAnalyzerViewModel
import rs.smobile.speak2act.viewmodel.RecorderViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var recorderViewModel: RecorderViewModel
    private lateinit var billAnalyzerViewModel: BillAnalyzerViewModel

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val pickImageLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                uri?.let { imageUri ->
                    val image = InputImage.fromFilePath(this, imageUri)
                    billAnalyzerViewModel.analyzeBill(image)
                }
            }

        setContent {
            recorderViewModel = hiltViewModel()
            billAnalyzerViewModel = hiltViewModel()
            val uiState by recorderViewModel.uiState.collectAsStateWithLifecycle()
            val amplitudes by recorderViewModel.amplitudes.collectAsStateWithLifecycle()
            val isRecording by recorderViewModel.isRecording.collectAsStateWithLifecycle()
            val bill by billAnalyzerViewModel.bill.collectAsStateWithLifecycle()

            val action = remember { mutableStateOf<Action?>(null) }

            val backgroundGradient = Brush.linearGradient(
                colors = listOf(
                    BackgroundDarkTop,
                    BackgroundDarkBottom
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            )

            val screenModifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)

            BackHandler {
                recorderViewModel.clearData()
                billAnalyzerViewModel.clearData()
                action.value = null
            }

            Speak2ActTheme {
                Box(
                    modifier = screenModifier,
                ) {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        text = when (action.value) {
                                            Action.VOICE_TO_ACTION -> stringResource(R.string.voice_intent_action)
                                            Action.BILL_ANALYZER -> stringResource(R.string.bill_summary)
                                            null -> stringResource(R.string.choose_action)
                                        },
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
                        when (action.value) {
                            Action.VOICE_TO_ACTION -> VoiceRecorderScreen(
                                innerPadding = innerPadding,
                                uiState = uiState,
                                isRecording = isRecording,
                                amplitudes = amplitudes,
                                onRecordClick = {
                                    if (isRecording) {
                                        recorderViewModel.stopRecording()
                                    } else {
                                        recorderViewModel.startRecording()
                                    }
                                }
                            )

                            Action.BILL_ANALYZER -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bill == null) {
                                        pickImageLauncher.launch("image/*")
                                        CircularProgressIndicator()
                                    }
                                    bill?.let {
                                        BillScreen(
                                            innerPadding = innerPadding,
                                            bill = it
                                        )
                                    }
                                }
                            }

                            null -> ActionSelectionScreen(
                                onSpeechClick = { action.value = Action.VOICE_TO_ACTION },
                                onBillClick = { action.value = Action.BILL_ANALYZER }
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class Action {
    VOICE_TO_ACTION,
    BILL_ANALYZER
}