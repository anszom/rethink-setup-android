package io.github.anszom.rethink.setup.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.Inet4Address

/**
 * Tracks the phone's current Wi-Fi network and lets callers poll its IPv4 address.
 *
 * Unlike a one-shot [requestWifiNetwork] lease, this keeps following the Wi-Fi
 * transport as the user switches access points (e.g. from home Wi-Fi to the
 * appliance AP during provisioning). INTERNET is not required, so it also selects
 * the appliance AP, which has none.
 *
 * Not thread-safe; drive it from the main thread.
 */
class WifiMonitor(context: Context) {

    private val cm =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    private var callback: ConnectivityManager.NetworkCallback? = null

    /** The Wi-Fi network currently bound, or null when none is available. */
    @Volatile
    var network: Network? = null
        private set

    fun start() {
        if (callback != null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(net: Network) {
                network = net
            }

            override fun onLost(net: Network) {
                if (net == network) network = null
            }
        }
        callback = cb
        cm.requestNetwork(request, cb)
    }

    /** The phone's own IPv4 address on the current Wi-Fi network, or null. */
    fun ipv4Address(): Inet4Address? {
        val net = network ?: return null
        return cm.getLinkProperties(net)
            ?.linkAddresses
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull()
    }

    fun stop() {
        callback?.let {
            try {
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        callback = null
        network = null
    }
}
