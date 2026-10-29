package rs.smobile.speak2act.core.ui

import android.app.Application
import com.cloudinary.android.MediaManager
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.initialize
import dagger.hilt.android.HiltAndroidApp
import rs.smobile.speak2act.BuildConfig
import rs.smobile.speak2act.core.security.appCheckProviderFactory

@HiltAndroidApp
class Speak2ActApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initAppCheck()
        initCloudinary()
        initFilament()
    }

    /**
     * Must run before any Firebase AI Logic call: the project enforces App Check, so unattested
     * requests are rejected. Debug builds use the debug provider (register its token in the
     * Firebase console), release builds use Play Integrity.
     */
    private fun initAppCheck() {
        Firebase.initialize(this)
        Firebase.appCheck.installAppCheckProviderFactory(appCheckProviderFactory())
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