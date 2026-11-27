package com.rds.mews.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(hasInternet)
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    suspend fun checkHubHealth(
        hubAddress: String,
        serverKey: String,
        enableProxy: Boolean = false
    ): Boolean = coroutineScope {
        val httpClient = SharedHttpClient.createInstance(hubAddress, serverKey, enableProxy)
        try {
            val res = httpClient.get("http://$hubAddress/?key=$serverKey")
            res.status in 200..399
        } catch (_: Exception) {
            false
        } finally {
            httpClient.close()
        }
    }

    suspend fun checkParserHealth(
        minifluxAddress: String,
        serverKey: String,
        enableProxy: Boolean = false
    ): Boolean = coroutineScope {
        val httpClient = SharedHttpClient.createInstance(minifluxAddress, serverKey, enableProxy)
        try {
            val addr = minifluxAddress.trim().removeSuffix("/")
            val schemeAddr = if (addr.startsWith("http://") || addr.startsWith("https://")) addr else "https://$addr"
            val res = httpClient.get("$schemeAddr/healthcheck")
            res.status in 200..399
        } catch (_: Exception) {
            false
        } finally {
            httpClient.close()
        }
    }

    suspend fun checkServersHealth(
        hubAddress: String,
        minifluxAddress: String,
        serverKey: String,
        enableProxy: Boolean = false
    ): Boolean = coroutineScope {
        val hubDeferred = async(Dispatchers.IO) { checkHubHealth(hubAddress, serverKey, enableProxy) }
        val parserDeferred = async(Dispatchers.IO) { checkParserHealth(minifluxAddress, serverKey, enableProxy) }
        hubDeferred.await() && parserDeferred.await()
    }
}
