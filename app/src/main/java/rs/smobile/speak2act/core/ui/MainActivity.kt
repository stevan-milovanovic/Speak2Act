package rs.smobile.speak2act.core.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.AndroidEntryPoint
import rs.smobile.speak2act.R
import rs.smobile.speak2act.core.theme.BackgroundDarkBottom
import rs.smobile.speak2act.core.theme.BackgroundDarkTop
import rs.smobile.speak2act.core.theme.Speak2ActTheme
import rs.smobile.speak2act.core.ui.AppDestination.ActionFigure
import rs.smobile.speak2act.core.ui.AppDestination.Bill
import rs.smobile.speak2act.core.ui.AppDestination.Home
import rs.smobile.speak2act.core.ui.AppDestination.Voice
import rs.smobile.speak2act.feature.actionfigure.ui.ActionFigure3DViewModel
import rs.smobile.speak2act.feature.actionfigure.ui.ActionFigureScreen
import rs.smobile.speak2act.feature.actionfigure.ui.ActionFigureViewModel
import rs.smobile.speak2act.feature.actionfigure.ui.PickImageScreen
import rs.smobile.speak2act.feature.billanalyzer.ui.BillAnalyzerViewModel
import rs.smobile.speak2act.feature.billanalyzer.ui.BillScreen
import rs.smobile.speak2act.feature.intro.ActionSelectionScreen
import rs.smobile.speak2act.feature.voicerecorder.ui.RecorderViewModel
import rs.smobile.speak2act.feature.voicerecorder.ui.VoiceRecorderScreen
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val navController = rememberNavController()
            val currentDestination = navController.currentDestination()
            val title = stringResource(currentDestination?.titleRes ?: Home.titleRes)

            val backgroundGradient = Brush.linearGradient(
                colors = listOf(BackgroundDarkTop, BackgroundDarkBottom),
                start = Offset.Zero,
                end = Offset.Infinite
            )

            val screenModifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)

            Speak2ActTheme {
                Box(modifier = screenModifier) {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        text = title,
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
                        NavHost(
                            navController = navController,
                            startDestination = Home
                        ) {
                            composable<Home> {
                                ActionSelectionScreen(
                                    onSpeechClick = {
                                        navController.navigate(Voice)
                                    },
                                    onBillClick = {
                                        navController.navigate(Bill)
                                    },
                                    onGenerateActionFigure = {
                                        navController.navigate(ActionFigure)
                                    }
                                )
                            }

                            composable<Voice> {
                                VoiceRoute(
                                    innerPadding = innerPadding,
                                    onBack = navController::popBackStack
                                )
                            }

                            composable<Bill> {
                                BillRoute(
                                    innerPadding = innerPadding,
                                    onBack = navController::popBackStack
                                )
                            }

                            composable<ActionFigure> {
                                ActionFigureRoute(
                                    innerPadding = innerPadding,
                                    onBack = navController::popBackStack
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceRoute(
    innerPadding: PaddingValues,
    onBack: () -> Unit
) {
    val recorderViewModel: RecorderViewModel = hiltViewModel()
    val uiState by recorderViewModel.uiState.collectAsStateWithLifecycle()
    val amplitudes by recorderViewModel.amplitudes.collectAsStateWithLifecycle()
    val isRecording by recorderViewModel.isRecording.collectAsStateWithLifecycle()

    BackHandler {
        recorderViewModel.clearData()
        onBack()
    }

    VoiceRecorderScreen(
        innerPadding = innerPadding,
        uiState = uiState,
        isRecording = isRecording,
        amplitudes = amplitudes,
        onRecordClick = {
            if (isRecording) recorderViewModel.stopRecording() else recorderViewModel.startRecording()
        }
    )
}

@Composable
private fun BillRoute(
    innerPadding: PaddingValues,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val billAnalyzerViewModel: BillAnalyzerViewModel = hiltViewModel()
    val bill by billAnalyzerViewModel.bill.collectAsStateWithLifecycle()
    val hasLaunchedPicker = remember { mutableStateOf(false) }

    val billImageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { imageUri ->
                val image = InputImage.fromFilePath(context, imageUri)
                billAnalyzerViewModel.analyzeBill(image)
            } ?: run {
                hasLaunchedPicker.value = false
            }
        }

    LaunchedEffect(bill) {
        if (bill == null && !hasLaunchedPicker.value) {
            hasLaunchedPicker.value = true
            billImageLauncher.launch("image/*")
        }
    }

    BackHandler {
        billAnalyzerViewModel.clearData()
        onBack()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (bill == null) {
            CircularProgressIndicator()
            if (!hasLaunchedPicker.value) {
                PickImageScreen(
                    modifier = Modifier
                        .padding(16.dp),
                    titleResId = R.string.select_a_bill_to_be_analysed,
                    onPickImage = {
                        hasLaunchedPicker.value = true
                        billImageLauncher.launch("image/*")
                    }
                )
            }
        }
        bill?.let {
            BillScreen(
                innerPadding = innerPadding,
                bill = it
            )
        }
    }
}

@Composable
private fun ActionFigureRoute(
    innerPadding: PaddingValues,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val actionFigureViewModel: ActionFigureViewModel = hiltViewModel()
    val actionFigure3DViewModel: ActionFigure3DViewModel = hiltViewModel()

    val actionFigureUiState by actionFigureViewModel.uiState.collectAsStateWithLifecycle()
    val imageUploadState by actionFigureViewModel.imageUploadState.collectAsStateWithLifecycle()
    val actionFigureGenerationState by actionFigureViewModel.actionFigureGenerationState.collectAsStateWithLifecycle()
    val actionFigure3dModel by actionFigure3DViewModel.uiState.collectAsStateWithLifecycle()
    val actionFigure3dModelProgressState by actionFigure3DViewModel.progressState.collectAsStateWithLifecycle()

    val actionFigureImageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { imageUri ->
                actionFigureViewModel.uploadImage(context.copyPickerUriToCache(imageUri))
            }
        }

    BackHandler(onBack = onBack)

    ActionFigureScreen(
        innerPadding = innerPadding,
        uiState = actionFigureUiState,
        imageUploadState = imageUploadState,
        actionFigureGenerationState = actionFigureGenerationState,
        actionFigure3dModel = actionFigure3dModel,
        actionFigure3dModelProgressState = actionFigure3dModelProgressState,
        onPickImage = { actionFigureImageLauncher.launch("image/*") },
        onGenerate = actionFigureViewModel::generate,
        onGenerateModel = { imageUrl ->
            actionFigureViewModel.generateModel()
            actionFigure3DViewModel.create3DModel(imageUrl)
        },
        onOrder = { actionFigure3DViewModel.createTextured3DModel("") }
    )
}

@Composable
private fun NavHostController.currentDestination(): AppDestination? {
    val navBackStackEntry by currentBackStackEntryAsState()
    return navBackStackEntry?.toRouteOrNull()
}

private fun Context.copyPickerUriToCache(uri: Uri): Uri {
    val input = contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("Cannot open input stream")
    val file = File(cacheDir, "picked_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { output ->
        input.use { it.copyTo(output) }
    }
    return file.toUri()
}

private fun NavBackStackEntry.toRouteOrNull(): AppDestination? = when {
    destination.hasRoute<Home>() -> Home
    destination.hasRoute<Voice>() -> Voice
    destination.hasRoute<Bill>() -> Bill
    destination.hasRoute<ActionFigure>() -> ActionFigure
    else -> null
}