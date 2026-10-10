package com.pulsestream.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** Rough network quality from the connection's reported link speed (no data used). */
enum class SpeedLevel { OFFLINE, SLOW, OK, FAST }

/**
 * Emits the current [SpeedLevel] whenever connectivity changes.
 * SLOW = red (bad), OK = yellow (normal), FAST = green (good for 1080p+).
 */
fun observeSpeedLevel(context: Context) = callbackFlow {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun level(): SpeedLevel {
        val net = cm.activeNetwork ?: return SpeedLevel.OFFLINE
        val caps = cm.getNetworkCapabilities(net) ?: return SpeedLevel.OFFLINE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return SpeedLevel.OFFLINE
        }
        return when (val kbps = caps.linkDownstreamBandwidthKbps) {
            // 0/unknown: assume usable but not fast.
            0 -> SpeedLevel.OK
            in 1 until 1500 -> SpeedLevel.SLOW
            in 1500 until 6000 -> SpeedLevel.OK
            else -> SpeedLevel.FAST
        }
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(level()) }
        override fun onLost(network: Network) { trySend(level()) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            trySend(level())
        }
    }
    cm.registerDefaultNetworkCallback(callback)
    trySend(level())
    awaitClose { cm.unregisterNetworkCallback(callback) }
}
