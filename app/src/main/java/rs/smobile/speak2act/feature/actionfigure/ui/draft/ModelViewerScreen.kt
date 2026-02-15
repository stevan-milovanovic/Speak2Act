package rs.smobile.speak2act.feature.actionfigure.ui.draft

import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.utils.ModelViewer
import rs.smobile.speak2act.R
import rs.smobile.speak2act.core.theme.Speak2ActTheme
import rs.smobile.speak2act.feature.actionfigure.data.setIblLights
import rs.smobile.speak2act.feature.actionfigure.data.setSkyboxLights
import rs.smobile.speak2act.feature.actionfigure.ui.draft.component.FilamentSurfaceView
import java.nio.ByteBuffer


@Composable
fun ModelViewerScreen(
    modelByteArray: ByteArray?,
    onOrder: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var modelViewer by remember { mutableStateOf<ModelViewer?>(null) }

    DisposableEffect(lifecycleOwner) {
        val choreographer = Choreographer.getInstance()
        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                modelViewer?.render(frameTimeNanos)
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(frameCallback)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> choreographer.removeFrameCallback(frameCallback)
                Lifecycle.Event.ON_RESUME -> choreographer.postFrameCallback(frameCallback)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            choreographer.removeFrameCallback(frameCallback)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
            if (modelByteArray == null) {
                CircularProgressIndicator(
                    Modifier
                        .size(160.dp),
                    strokeWidth = 8.dp
                )
            } else {
                AndroidView(
                    factory = {
                        val surfaceView = FilamentSurfaceView(context)
                        val mv = ModelViewer(surfaceView)
                        mv.setIblLights(context)
                        mv.setSkyboxLights(context)
                        mv.loadModelGlb(modelByteArray.toDirectByteBuffer())

                        val pinchToZoomListener = PinchToZoomListener(mv.camera)
                        val scaleDetector = ScaleGestureDetector(context, pinchToZoomListener)

                        val listener = View.OnTouchListener { v, event ->
                            scaleDetector.onTouchEvent(event)
                            mv.onTouchEvent(event)
                            if (event.action == MotionEvent.ACTION_UP) v.performClick()
                            true
                        }
                        modelViewer = mv
                        surfaceView.setOnTouchListener(listener)
                        surfaceView
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .aspectRatio(9 / 16f)
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onOrder,
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.order_action_figure))
        }
    }
}

@Preview
@Composable
private fun ModelViewerScreenPreview() {
    Speak2ActTheme {
        ModelViewerScreen(null) {}
    }
}

private fun ByteArray.toDirectByteBuffer(): ByteBuffer {
    val buffer = ByteBuffer.allocateDirect(size)
    buffer.put(this)
    buffer.flip()
    return buffer
}