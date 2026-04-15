package com.lush1us.podder.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the device currently has a validated internet connection.
 * Singleton — register once in Koin; collect [isOffline] in ViewModels or Composables.
 *
 * "Offline" = no network with both INTERNET and VALIDATED capabilities. A connected-but-captive-portal
 * network (e.g. hotel WiFi before login) reports as offline, which is what we want.
 */
class NetworkObserver(context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOffline = MutableStateFlow(computeOffline())
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { recompute() }
        override fun onLost(network: Network) { recompute() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { recompute() }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
    }

    private fun recompute() { _isOffline.value = computeOffline() }

    private fun computeOffline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        val hasInternet  = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated  = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return !(hasInternet && isValidated)
    }
}
