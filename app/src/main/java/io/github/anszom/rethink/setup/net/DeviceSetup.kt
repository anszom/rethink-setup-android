package io.github.anszom.rethink.setup.net

import android.net.Network
import android.util.Base64
import java.util.TimeZone
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Provisions an LG appliance, replicating `rethink-setup.ts`.
 *
 * Appliances on a [SoftAp.THINQ] access point speak ThinQ1 or ThinQ2. ThinQ1 (mTosp/XML) is
 * attempted first; its framing should be rejected by ThinQ2 appliances, in which case we fall
 * back to the ThinQ2 (JSON) handshake. Appliances on a [SoftAp.WHISEN] access point speak the
 * Whisen HTTP-over-TLS protocol instead.
 *
 * All socket I/O runs on [Dispatchers.IO]. Progress is reported through [log].
 */
object DeviceSetup {

    /**
     * The flavours of appliance access point. Upstream tries both protocols at once against a
     * single host; here the SoftAP is identified up front by the subnet its DHCP hands out, since
     * the two listen on different addresses.
     *
     * TODO: confirm that Whisen appliances use 192.168.1.x exclusively (and ThinQ ones
     * 192.168.120.x). Upstream only documents one Whisen unit (RAC_056905_WW) at 192.168.1.1 and
     * does not tie the protocol to the subnet.
     */
    enum class SoftAp(val host: String, val port: Int) {
        /** ThinQ1 / ThinQ2 appliances. */
        THINQ("192.168.120.254", 5500),

        /** Whisen appliances (QCA4002 module, e.g. RAC_056905_WW), which don't listen on 5500. */
        WHISEN("192.168.1.1", Whisen.PORT);

        /** DHCP on this AP hands out addresses in this /24, e.g. "192.168.120." */
        val subnetPrefix = host.substringBeforeLast('.') + "."

        companion object {
            /** The SoftAP whose subnet contains the phone's [ip], or null. */
            fun forAddress(ip: String): SoftAp? = entries.firstOrNull { ip.startsWith(it.subnetPrefix) }
        }
    }

    private const val IO_TIMEOUT_MS = 8000
    private const val WHISEN_TIMEOUT_MS = 15000

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
        softAp: SoftAp,
        ssid: String,
        password: String,
        log: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val host = softAp.host
        val port = softAp.port
        when (softAp) {
            SoftAp.THINQ -> try {
                log("Trying ThinQ 1 setup")
                thinq1(network, host, port, ssid, password, log)
            } catch (e: Exception) {
                log("ThinQ 1 setup failed: ${e.message}")
                log("Trying ThinQ 2 setup")
                thinq2(network, host, port, ssid, password, log)
            }

            SoftAp.WHISEN -> whisen(network, host, port, ssid, password, log)
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
                val msg = obj.toString()
                log("> $msg")
                out.write(msg.toByteArray(Charsets.UTF_8))
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
                        log("< $msg")
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

    // --- Whisen (HTTP over TLS) -----------------------------------------------

    private fun whisen(
        network: Network,
        host: String,
        port: Int,
        ssid: String,
        password: String,
        log: (String) -> Unit,
    ) {
        fun request(path: String, body: String, headers: List<String>? = null): String =
            whisenRequest(network, host, port, path, Whisen.request(path, body, headers), log)

        log("Connecting to $host:$port")
        request("/SetDeviceInit", "")

        // Answers 500 on the RAC_056905_WW firmware
        Whisen.parseMembers(request("/GetDeviceInfo", ""))?.let { log("device info: $it") }

        // Nation is the country code, sent as subCountryCode by the other setups. The RAC_056905_WW
        // firmware ignores regionalCode and picks its server from Nation: DE makes it connect to
        // eic.lgthinq.com. Must precede SetDeviceConfig: ReleaseDevAp at the end takes the SoftAP down.
        val infoReply = request("/SetDeviceInfo", Whisen.deviceInfoBody("DE", "rethink"))
        if (Whisen.statusCode(infoReply) != 200) throw IllegalStateException("SetDeviceInfo rejected")

        val offsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
        val cfgReply = request(
            "/SetDeviceConfig",
            Whisen.deviceConfigBody(ssid, password, Whisen.timezone(offsetMinutes)),
            Whisen.DEVICE_CONFIG_HEADERS,
        )
        if (Whisen.statusCode(cfgReply) != 200) {
            throw IllegalStateException("SetDeviceConfig rejected, appliance left in AP mode")
        }

        // The appliance often drops its SoftAP before the reply reaches us ("Software caused
        // connection abort"). Like the LG app, ignore any failure here: success was already decided
        // by SetDeviceConfig, where the appliance accepted the Wi-Fi details.
        try {
            request("/ReleaseDevAp", "")
        } catch (e: Exception) {
            log("ReleaseDevAp failed (ignored, the appliance has likely left AP mode): ${e.message}")
        }
        log("Whisen setup successful, see rethink-cloud logs for a follow-up")
    }

    /** Opens a fresh TLS connection, sends one request and returns everything read until the appliance closes it. */
    private fun whisenRequest(
        network: Network,
        host: String,
        port: Int,
        path: String,
        request: ByteArray,
        log: (String) -> Unit,
    ): String {
        val socket = Tls.connect(network, host, port, WHISEN_TIMEOUT_MS, tls12Only = true)
        try {
            log("Request: POST $path")
            socket.outputStream.write(request)
            socket.outputStream.flush()
            val reply = socket.inputStream.readBytes().toString(Charsets.UTF_8)
            log("response: ${JSONObject.quote(reply)}")
            return reply
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
