package io.github.anszom.rethink.setup.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.io.Closeable
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * A held reference to a Wi-Fi [Network]. The underlying network callback stays
 * registered until [close] so the system keeps the network available (and routable
 * for sockets bound to it). Always use it inside `use { ... }`.
 */
class WifiNetworkLease internal constructor(
    val network: Network,
    private val cm: ConnectivityManager,
    private val callback: ConnectivityManager.NetworkCallback,
) : Closeable {
    override fun close() {
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }
}

/**
 * Requests a Wi-Fi network and suspends until one is available.
 *
 * @param requireInternet true for the DNS-redirection check (home Wi-Fi, which has
 *        internet); false when talking to an appliance AP, which has none — requiring
 *        internet there would never resolve.
 * @throws kotlinx.coroutines.TimeoutCancellationException if no matching Wi-Fi
 *         network appears in time (e.g. the phone is on mobile data only).
 */
suspend fun requestWifiNetwork(
    context: Context,
    requireInternet: Boolean,
    timeoutMs: Long = 15_000,
): WifiNetworkLease {
    val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val builder = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    if (requireInternet) {
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } else {
        builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    val request = builder.build()

    var registered: ConnectivityManager.NetworkCallback? = null
    try {
        return withTimeout(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (cont.isActive) cont.resume(WifiNetworkLease(network, cm, this))
                    }
                }
                registered = callback
                cm.requestNetwork(request, callback)
                cont.invokeOnCancellation {
                    try {
                        cm.unregisterNetworkCallback(callback)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    } catch (e: Throwable) {
        registered?.let {
            try {
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        throw e
    }
}
