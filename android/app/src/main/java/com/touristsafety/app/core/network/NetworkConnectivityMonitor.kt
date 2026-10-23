package com.touristsafety.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkStatus {
    NO_NETWORK,
    CONNECTED_NO_INTERNET,
    INTERNET_AVAILABLE,
    BACKEND_REACHABLE
}

data class DetailedConnectivityState(
    val status: NetworkStatus = NetworkStatus.NO_NETWORK,
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val backendPingMs: Long? = null,
    val lastCheckedTimestamp: Long = System.currentTimeMillis()
)

@Singleton
class NetworkConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverConfigManager: ServerConfigManager
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pingClient = OkHttpClient.Builder().build()

    private val _state = MutableStateFlow(DetailedConnectivityState())
    val state: StateFlow<DetailedConnectivityState> = _state.asStateFlow()

    init {
        registerNetworkCallback()
        checkInitialState()
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                evaluateNetwork(network)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                evaluateCapabilities(capabilities)
            }

            override fun onLost(network: Network) {
                _state.value = DetailedConnectivityState(
                    status = NetworkStatus.NO_NETWORK,
                    isWifi = false,
                    isCellular = false,
                    lastCheckedTimestamp = System.currentTimeMillis()
                )
            }
        })
    }

    private fun checkInitialState() {
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            _state.value = DetailedConnectivityState(status = NetworkStatus.NO_NETWORK)
            return
        }
        evaluateNetwork(activeNetwork)
    }

    private fun evaluateNetwork(network: Network) {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return
        evaluateCapabilities(capabilities)
    }

    private fun evaluateCapabilities(capabilities: NetworkCapabilities) {
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        val rawStatus = when {
            !hasInternet -> NetworkStatus.NO_NETWORK
            !isValidated -> NetworkStatus.CONNECTED_NO_INTERNET
            else -> NetworkStatus.INTERNET_AVAILABLE
        }

        _state.value = DetailedConnectivityState(
            status = rawStatus,
            isWifi = isWifi,
            isCellular = isCellular,
            lastCheckedTimestamp = System.currentTimeMillis()
        )

        // Asynchronously probe backend reachability
        scope.launch {
            probeBackend()
        }
    }

    suspend fun probeBackend() {
        val cur = _state.value
        val isWifi = cur.isWifi
        val isCellular = cur.isCellular
        val baseUrl = serverConfigManager.getCurrentUrl()
        val healthUrl = "${baseUrl}health/live"

        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder().url(healthUrl).build()
            val resp = pingClient.newCall(req).execute()
            val ping = System.currentTimeMillis() - start
            if (resp.isSuccessful) {
                _state.value = DetailedConnectivityState(
                    status = NetworkStatus.BACKEND_REACHABLE,
                    isWifi = isWifi,
                    isCellular = isCellular,
                    backendPingMs = ping,
                    lastCheckedTimestamp = System.currentTimeMillis()
                )
            }
            resp.close()
        } catch (e: Exception) {
            // Keep current internet status if backend unreachable
            val cur = _state.value
            if (cur.status == NetworkStatus.BACKEND_REACHABLE) {
                _state.value = cur.copy(status = NetworkStatus.INTERNET_AVAILABLE, backendPingMs = null)
            }
        }
    }
}
