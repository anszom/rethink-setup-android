package io.github.anszom.rethink.setup.net

import android.net.Network
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of probing the LG route endpoint for the Rethink marker header. */
data class RouteResult(
    val redirected: Boolean,
    val headerValue: String?,
    val httpStatus: Int,
    val body: String,
)

/**
 * Probes the LG cloud endpoints over the given (Wi-Fi) [network] to tell whether LG's
 * DNS is being intercepted by Rethink. There are two independent legs — one per ThinQ
 * generation — because the two appliance families reach Rethink over different hosts/ports.
 *
 * Detection on both legs is by status code: a 200 means we reached Rethink. See the
 * per-method KDoc for what the official backend returns instead.
 */
object RouteCheck {
    private const val ROUTE_URL = "https://common.lgthinq.com/route"
    private const val THINQ1_URL = "https://rethink.lgthinq.com:46030/rethink"
    private const val HEADER = "x-rethink"
    // private const val EXPECTED = "cloud-free"

    /**
     * ThinQ2 leg: fetches https://common.lgthinq.com/route.
     *
     * The official backend rejects requests that lack the `x-service-phase` header with
     * HTTP 400, whereas Rethink answers 200. We deliberately send no `x-service-phase`
     * header, so a 200 means we reached Rethink.
     */
    suspend fun check(network: Network): RouteResult = withContext(Dispatchers.IO) {
        fetch(network, ROUTE_URL)
    }

    /**
     * ThinQ1 leg: fetches https://rethink.lgthinq.com:46030/rethink.
     *
     * The primary signal here is DNS resolution. `rethink.lgthinq.com` is a fake hostname
     * that only resolves when redirection to Rethink is configured; in an unconfigured or
     * misconfigured environment the lookup simply fails, and the caller treats that thrown
     * failure as a negative result (not a runtime error). When redirection is in place the
     * name resolves to Rethink, which answers 200. 
     */
    suspend fun checkThinq1(network: Network): RouteResult = withContext(Dispatchers.IO) {
        fetch(network, THINQ1_URL)
    }

    private fun fetch(network: Network, url: String): RouteResult {
        val conn = network.openConnection(URL(url)) as HttpsURLConnection
        try {
            // Rethink serves the intercepted host with a self-signed certificate, so
            // trust any certificate/hostname here just as the provisioning path does.
            conn.sslSocketFactory = Tls.sslSocketFactory
            conn.hostnameVerifier = Tls.allowAllHostnames
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = false
            val status = conn.responseCode
            val header = conn.getHeaderField(HEADER)
            val stream = if (status in 200..399) conn.inputStream else conn.errorStream
            val body = stream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
            return RouteResult(
                // Older Rethink installations don't set the x-rethink header, so the
                // header check below produced false negatives. Detect by status instead:
                // 200 = Rethink, non-200 = official backend.
                // redirected = header.equals(EXPECTED, ignoreCase = true),
                redirected = status == 200,
                headerValue = header,
                httpStatus = status,
                body = body,
            )
        } finally {
            conn.disconnect()
        }
    }
}
