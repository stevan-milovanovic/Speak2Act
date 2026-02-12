package rs.smobile.speak2act.feature.actionfigure.data

import android.content.Context
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
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

fun ModelViewer.setIblLights(context: Context) {
    val buffer = context.assets.open("lightroom_14b_ibl.ktx").use {
        it.readBytes().let { bytes -> ByteBuffer.wrap(bytes) }
    }

    val iblBundle = KTX1Loader.createIndirectLight(engine, buffer).apply {
        indirectLight?.intensity = 40_000f
    }

    scene.indirectLight = iblBundle.indirectLight
}

fun ModelViewer.setSkyboxLights(context: Context) {
    val buffer = context.assets.open("lightroom_14b_skybox.ktx").use {
        it.readBytes().let { bytes -> ByteBuffer.wrap(bytes) }
    }

    val skyboxBundle = KTX1Loader.createSkybox(engine, buffer)

    scene.skybox = skyboxBundle.skybox
}