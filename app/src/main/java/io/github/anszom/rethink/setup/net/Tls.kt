package io.github.anszom.rethink.setup.net

import android.net.Network
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * TLS connector for the appliance's provisioning endpoint.
 *
 * The appliances present a self-signed certificate and negotiate ciphersuites that
 * modern stacks disable by default, so we (a) trust any certificate — the upstream
 * `rethink-setup.ts` uses `rejectUnauthorized: false` for the same reason — and
 * (b) re-enable every protocol/ciphersuite the platform still supports.
 *
 * The socket is created from [Network.getSocketFactory] so traffic is pinned to the
 * appliance's Wi-Fi. Without this, Android would route to the device's default
 * network (mobile data) because the appliance AP has no internet.
 */
object Tls {

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /** Socket factory that trusts any certificate. Reused by the redirect check. */
    val sslSocketFactory: SSLSocketFactory by lazy {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf(trustAllManager), SecureRandom())
        ctx.socketFactory
    }

    /** Hostname verifier that accepts any hostname (pairs with [sslSocketFactory]). */
    val allowAllHostnames = HostnameVerifier { _, _ -> true }

    fun connect(network: Network, host: String, port: Int, timeoutMs: Int): SSLSocket {
        val plain = network.socketFactory.createSocket()
        plain.connect(InetSocketAddress(host, port), timeoutMs)

        val ssl = sslSocketFactory.createSocket(plain, host, port, true) as SSLSocket
        // Offer everything we can so the appliance's outdated suite is on the table.
        ssl.enabledProtocols = ssl.supportedProtocols
        ssl.enabledCipherSuites = ssl.supportedCipherSuites
        // Raw SSLSocket does no hostname verification anyway; make that explicit.
        ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = null }
        ssl.soTimeout = timeoutMs
        ssl.startHandshake()
        return ssl
    }
}
