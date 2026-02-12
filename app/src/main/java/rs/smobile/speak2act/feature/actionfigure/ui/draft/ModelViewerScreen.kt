package rs.smobile.speak2act.feature.actionfigure.ui.draft

import android.annotation.SuppressLint
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.utils.ModelViewer
import rs.smobile.speak2act.core.theme.Speak2ActTheme
import rs.smobile.speak2act.feature.actionfigure.data.loadGlbFromAssets
import rs.smobile.speak2act.feature.actionfigure.data.setIblLights
import rs.smobile.speak2act.feature.actionfigure.data.setSkyboxLights


@SuppressLint("ClickableViewAccessibility")
@Composable
fun ModelViewerScreen(
    modelAssetName: String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val surfaceView = remember {
        SurfaceView(context)
    }

    val modelViewer = remember {
        ModelViewer(surfaceView)
    }

    var isEngineDestroyed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val pinchToZoomListener = PinchToZoomListener(modelViewer.camera)
        val scaleDetector = ScaleGestureDetector(context, pinchToZoomListener)
        surfaceView.setOnTouchListener { v, event ->
            if (!isEngineDestroyed) {
                scaleDetector.onTouchEvent(event)
                modelViewer.onTouchEvent(event)
            }
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            true
        }
        modelViewer.setIblLights(context)
        modelViewer.setSkyboxLights(context)
        modelViewer.loadGlbFromAssets(context, modelAssetName)
    }

    DisposableEffect(lifecycleOwner) {
        var frameCallback: Choreographer.FrameCallback? = null

        fun startRendering() {
            frameCallback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (!isEngineDestroyed) {
                        modelViewer.render(frameTimeNanos)
                        Choreographer.getInstance().postFrameCallback(this)
                    }
                }
            }
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        fun stopRendering() {
            frameCallback?.let {
                Choreographer.getInstance().removeFrameCallback(it)
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> startRendering()
                Lifecycle.Event.ON_PAUSE -> stopRendering()

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopRendering()
            if (!isEngineDestroyed) {
                isEngineDestroyed = true
                modelViewer.engine.destroy()
            }
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize(),
        factory = { surfaceView }
    )
}

@Preview
@Composable
private fun ModelViewerScreenPreview() {
    Speak2ActTheme {
        ModelViewerScreen("abc")
    }
}