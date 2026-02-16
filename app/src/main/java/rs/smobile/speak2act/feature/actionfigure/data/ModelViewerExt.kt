package rs.smobile.speak2act.feature.actionfigure.data

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
import rs.smobile.speak2act.feature.actionfigure.ui.draft.PinchToZoomListener
import java.nio.ByteBuffer


fun ModelViewer.loadGlbFromAssets(
    context: Context,
    assetName: String
) {
    val buffer = context.assets.open(assetName).use { input ->
        val bytes = input.readBytes()
        ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            rewind()
        }
    }

    loadModelGlb(buffer)
    transformToUnitCube()
}

fun ModelViewer.loadActionFigureModel(
    context: Context,
    modelByteArray: ByteArray
) {
    setIblLights(context)
    setSkyboxLights(context)
    loadModelGlb(modelByteArray.toDirectByteBuffer())
    transformToUnitCube()
}

fun ModelViewer.provideTouchListener(context: Context): View.OnTouchListener {
    val pinchToZoomListener = PinchToZoomListener(camera)
    val scaleDetector = ScaleGestureDetector(context, pinchToZoomListener)
    return View.OnTouchListener { v, event ->
        scaleDetector.onTouchEvent(event)
        onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) v.performClick()
        true
    }
}

private fun ModelViewer.setIblLights(context: Context) {
    val buffer = context.assets.open("lightroom_14b_ibl.ktx").use {
        it.readBytes().let { bytes -> ByteBuffer.wrap(bytes) }
    }

    val iblBundle = KTX1Loader.createIndirectLight(engine, buffer).apply {
        indirectLight?.intensity = 40_000f
    }

    scene.indirectLight = iblBundle.indirectLight
}

private fun ModelViewer.setSkyboxLights(context: Context) {
    val buffer = context.assets.open("lightroom_14b_skybox.ktx").use {
        it.readBytes().let { bytes -> ByteBuffer.wrap(bytes) }
    }

    val skyboxBundle = KTX1Loader.createSkybox(engine, buffer)

    scene.skybox = skyboxBundle.skybox
}

private fun ByteArray.toDirectByteBuffer(): ByteBuffer {
    val buffer = ByteBuffer.allocateDirect(size)
    buffer.put(this)
    buffer.flip()
    return buffer
}