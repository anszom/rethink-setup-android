package io.github.anszom.rethink.setup.net

import android.net.Network
import android.util.Base64
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Provisions an LG appliance, replicating `rethink-setup.ts`.
 *
 * ThinQ1 (mTosp/XML) is attempted first; its framing should be rejected by ThinQ2
 * appliances, in which case we fall back to the ThinQ2 (JSON) handshake.
 *
 * All socket I/O runs on [Dispatchers.IO]. Progress is reported through [log].
 */
object DeviceSetup {

    const val DEFAULT_HOST = "192.168.120.254"
    const val DEFAULT_PORT = 5500

    /** DHCP on the appliance AP hands out addresses in this /24, e.g. "192.168.120." */
    val EXPECTED_SUBNET_PREFIX = DEFAULT_HOST.substringBeforeLast('.') + "."

    private const val IO_TIMEOUT_MS = 8000

    // The public key used by the official LG cloud. We don't hold the private key and
    // don't need to verify anything, so reusing LG's key keeps setup simple — see the
    // note in rethink-setup.ts.
    private const val PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApYRAZXRWijMuWNr9LHOJ
fcPcZHDYcO3CwRF9olsPvtJpkrDXR7jEDA6qPHF1jvJ7ArxDLVj8rbkwXb3oXNmN
Sc+n0DPNDiRgghDaDyJpN0qfzmt06MKdihVScwghyYKWD+oA9d1+j3wy3W32he+X
7FnS+yUmmbQ8cT0PYS7p2E8YtbgHrH+SbUzHAgBbaS8E92l7f0qOpQFmYEyP/OX+
1n0dLdXXJ8kFxCLP2n8Wy6XXTutrT0YuZCxabPVYSKsjLh86MuHEM6V8BdBoZItW
qA1bDeDvjP7QC93lGxmwIYR0H8VVQq7gBZYWpPfsRSfwsE/PCMrF1WS4sPnSauaV
QwIDAQAB
-----END PUBLIC KEY-----
"""

    private fun b64(s: String): String =
        Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    suspend fun provision(
        network: Network,
        host: String,
        port: Int,
        ssid: String,
        password: String,
        log: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        try {
            log("Trying ThinQ 1 setup")
            thinq1(network, host, port, ssid, password, log)
        } catch (e: Exception) {
            log("ThinQ 1 setup failed: ${e.message}")
            log("Trying ThinQ 2 setup")
            thinq2(network, host, port, ssid, password, log)
        }
    }

    // --- ThinQ1 (mTosp / XML) -------------------------------------------------

    private fun thinq1(
        network: Network,
        host: String,
        port: Int,
        ssid: String,
        password: String,
        log: (String) -> Unit,
    ) {
        log("Connecting to $host:$port")
        log("Request: deviceinfo")
        var resp = thinq1Request(
            network, host, port,
            "<mTosp><data type=\"deviceinfo\"><time>${System.currentTimeMillis()}</time>" +
                "<reg>000</reg><errorCode>N</errorCode></data></mTosp>",
        )
        log("response: $resp")

        log("Request: apinfo")
        // The region code is a fake one, `rethink`, so the appliance attempts
        // connections to rethink.lgthinq.com.
        resp = thinq1Request(
            network, host, port,
            "<mTosp><data type=\"apinfo\">" +
                "<format>B64</format>" +
                "<bssid>${b64(ssid)}</bssid>" +
                "<security>WPA_PSK</security>" +
                "<password>${b64(password)}</password>" +
                "<subCountryCode>DE</subCountryCode>" +
                "<regionalCode>rethink</regionalCode>" +
                "</data></mTosp>",
        )
        log("response: $resp")
        log("ThinQ1 setup successful, see rethink-cloud logs for a follow-up")
    }

    /** Opens a fresh TLS connection, sends one mTosp frame and returns the reply payload. */
    private fun thinq1Request(network: Network, host: String, port: Int, xml: String): String {
        val socket = Tls.connect(network, host, port, IO_TIMEOUT_MS)
        try {
            socket.outputStream.write(Mtosp.format(xml))
            socket.outputStream.flush()
            return Mtosp.readFrame(socket.inputStream)
        } finally {
            closeQuietly(socket)
        }
    }

    // --- ThinQ2 (JSON) --------------------------------------------------------

    private fun thinq2(
        network: Network,
        host: String,
        port: Int,
        ssid: String,
        password: String,
        log: (String) -> Unit,
    ) {
        log("Connecting to $host:$port")
        val socket = Tls.connect(network, host, port, IO_TIMEOUT_MS)
        log("TLS connection established")
        try {
            val out = socket.outputStream
            fun send(obj: JSONObject) {
                out.write(obj.toString().toByteArray(Charsets.UTF_8))
                out.flush()
            }

            fun request(cmd: String, data: JSONObject): JSONObject =
                JSONObject().put("type", "request").put("cmd", cmd).put("data", data)

            send(request("setDeviceInit", JSONObject().put("set", "true").put("constantConnect", "Y")))

            var done = false
            val splitter = JsonSplitter()
            val buf = ByteArray(4096)

            while (!done) {
                val n = socket.inputStream.read(buf)
                if (n < 0) throw java.io.EOFException("connection closed before setup completed")
                for (i in 0 until n) {
                    splitter.feed(buf[i].toInt() and 0xff) { msg ->
                        val json = JSONObject(msg)
                        log(msg)
                        if (json.optString("type") != "response") return@feed

                        val result = json.optJSONObject("data")?.optString("result")
                        if (!result.isNullOrEmpty() && result != "000") {
                            throw IllegalStateException("Error code returned: $result")
                        }

                        when (json.optString("cmd")) {
                            "setDeviceInit" -> send(
                                request(
                                    "getDeviceInfo",
                                    JSONObject()
                                        .put("subCountryCode", "DE")
                                        .put("regionalCode", "eic")
                                        .put("timezone", "+0100")
                                        .put("publicKey", PUBLIC_KEY)
                                        .put("constantConnect", "Y"),
                                ),
                            )

                            "getDeviceInfo" -> send(
                                request(
                                    "setCertInfo",
                                    JSONObject()
                                        .put("otp", "0123456789abcdef0123456789abcdef0123456789abcdef")
                                        .put("svccode", "SVC202")
                                        .put("svcphase", "OP")
                                        .put("constantConnect", "Y"),
                                ),
                            )

                            "setCertInfo" -> send(
                                request(
                                    "setApInfo",
                                    JSONObject()
                                        .put("format", "B64")
                                        .put("ssid", b64(ssid))
                                        .put("password", b64(password))
                                        .put("security", "WPA2_PSK")
                                        .put("cipher", "AES")
                                        .put("constantConnect", "Y"),
                                ),
                            )

                            "setApInfo" -> send(request("releaseDev", JSONObject()))

                            "releaseDev" -> {
                                log("Setup completed, the device will now connect to your Wi-Fi")
                                log("ThinQ2 setup successful, see rethink-cloud logs for a follow-up")
                                done = true
                            }
                        }
                    }
                    if (done) break
                }
            }
        } finally {
            closeQuietly(socket)
        }
    }

    private fun closeQuietly(socket: SSLSocket) {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }
}
