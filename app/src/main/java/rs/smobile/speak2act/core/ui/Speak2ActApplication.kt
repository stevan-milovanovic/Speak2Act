package rs.smobile.speak2act.core.ui

import android.app.Application
import com.cloudinary.android.MediaManager
import dagger.hilt.android.HiltAndroidApp
import rs.smobile.speak2act.BuildConfig

@HiltAndroidApp
class Speak2ActApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initCloudinary()
        initFilament()
    }

    private fun initCloudinary() {
        val config = hashMapOf("cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME)
        MediaManager.init(this, config)
    }

    private fun initFilament() {
        // Force-load filament native libs
        System.loadLibrary("filament-jni")
        System.loadLibrary("filament-utils-jni")
    }
}