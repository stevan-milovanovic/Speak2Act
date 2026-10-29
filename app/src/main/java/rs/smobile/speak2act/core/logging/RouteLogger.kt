package rs.smobile.speak2act.core.logging

import android.util.Log
import javax.inject.Inject

/**
 * Logs under the `Speak2ActRoute` tag. Injected so that unit tests can run without `android.util.Log`.
 */
interface RouteLogger {
    fun log(message: String)
}

class AndroidRouteLogger @Inject constructor() : RouteLogger {
    override fun log(message: String) {
        Log.i(TAG, message)
    }

    private companion object {
        private const val TAG = "Speak2ActRoute"
    }
}
