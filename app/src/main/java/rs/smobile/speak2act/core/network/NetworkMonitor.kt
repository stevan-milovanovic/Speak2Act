package rs.smobile.speak2act.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Tells whether the device can currently reach the internet. */
interface NetworkMonitor {
    fun isOnline(): Boolean

    /** Whether the active network is unmetered (e.g. Wi-Fi); large downloads only happen then. */
    fun isUnmetered(): Boolean
}

/** [NetworkMonitor] backed by [ConnectivityManager]: online = internet capability, validated. */
class ConnectivityNetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NetworkMonitor {

    override fun isOnline(): Boolean {
        val capabilities = activeCapabilities() ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun isUnmetered(): Boolean =
        isOnline() && activeCapabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true

    private fun activeCapabilities(): NetworkCapabilities? {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    }
}
