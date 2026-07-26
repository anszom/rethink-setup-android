package io.github.anszom.rethink.setup.dns

import android.content.Context
import android.net.Network
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anszom.rethink.setup.net.Tls
import io.github.anszom.rethink.setup.net.requestWifiNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

/**
 * Probes the LG cloud endpoints over the given (Wi-Fi) [Network] to tell whether LG's
 * DNS is being intercepted by Rethink. There are two independent legs — one per ThinQ
 * generation — because the two appliance families reach Rethink over different hosts/ports.
 *
 * Detection on both legs is by status code: a 200 means we reached Rethink. See the
 * per-method KDoc for what the official backend returns instead.
 */
class RouteChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun checkDns(): Flow<String> = flow {
        try {
            emit("Looking for a Wi-Fi connection with internet…\n\n")
            requestWifiNetwork(context, true).use {
                emit("ThinQ1: fetching ${THINQ1_URL}…\n")
                emit(checkThinQ1(it.network))
                emit("ThinQ2: fetching ${THINQ2_URL}…\n")
                emit(checkThinQ2(it.network))
            }
        } catch (e: Exception) {
            emit("\nx Could not run the check over Wi-Fi: ${e.message}\nConnect this phone to your home Wi-Fi (not mobile data) and try again.\n\n")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * ThinQ2 leg: fetches https://common.lgthinq.com/route.
     *
     * The official backend rejects requests that lack the `x-service-phase` header with
     * HTTP 400, whereas Rethink answers 200. We deliberately send no `x-service-phase`
     * header, so a 200 means we reached Rethink.
     */
    private fun checkThinQ2(network: Network): String {
        return try {
            val result = fetch(network, THINQ2_URL)
            if (result.redirected) {
                "✓ DNS redirection is active for ThinQ2 — LG's cloud is being served locally by Rethink.\n\n"
            } else {
                "✗ Not fully redirected. Requests to LG still reach the real cloud.\nThinQ2 (common.lgthinq.com) is not redirected.\nCheck that this phone is on the Wi-Fi where Rethink runs and that DNS is pointed at it.\n\n"
            }
        } catch (e: Exception) {
            "$THINQ2_URL did not resolve / could not be reached: ${e.message}"
        }
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
    private fun checkThinQ1(network: Network): String {
        return try {
            val result = fetch(network, THINQ1_URL)
            if (result.redirected) {
                "✓ DNS redirection is active for ThinQ1 — LG's cloud is being served locally by Rethink.\n\n"
            } else {
                "✗ Not fully redirected. Requests to LG still reach the real cloud.\nThinQ1 (rethink.lgthinq.com:46030) is not redirected.\nCheck that this phone is on the Wi-Fi where Rethink runs and that DNS is pointed at it.\n\n"
            }
        } catch (e: Exception) {
            "$THINQ1_URL did not resolve / could not be reached: ${e.message}"
        }
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

    data class RouteResult(
        val redirected: Boolean,
        val headerValue: String?,
        val httpStatus: Int,
        val body: String,
    )

    companion object {
        private const val THINQ2_URL = "https://common.lgthinq.com/route"
        private const val THINQ1_URL = "https://rethink.lgthinq.com:46030/rethink"
        private const val HEADER = "x-rethink"
    }
}