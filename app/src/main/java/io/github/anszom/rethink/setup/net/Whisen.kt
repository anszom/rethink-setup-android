package io.github.anszom.rethink.setup.net

import org.json.JSONObject

/**
 * Framing for the Whisen SoftAP setup protocol, mirroring `util/whisen.ts` upstream: HTTP/1.1 POST
 * over TLS on port 9000, one request per connection, no authentication. Bodies are bare JSON
 * members separated by CRLF. SetDeviceConfig declares "Content-Length: 0" and an empty
 * "Session-Id:" although it carries a body; the LG app has always sent it that way, so it is
 * reproduced verbatim.
 */
object Whisen {

    const val PORT = 9000

    /** Headers the app puts on /SetDeviceConfig, body notwithstanding. */
    val DEVICE_CONFIG_HEADERS = listOf("Content-Length: 0", "Session-Id: ")

    /** [headers] defaults to a single Content-Length. */
    fun request(path: String, body: String, headers: List<String>? = null): ByteArray =
        (listOf("POST $path HTTP/1.1") +
            (headers ?: listOf("Content-Length: ${body.toByteArray(Charsets.UTF_8).size}")) +
            listOf("", body))
            .joinToString("\r\n")
            .toByteArray(Charsets.UTF_8)

    /** Timezone as the app formats it: sign, two-digit hours, two-digit minutes, e.g. "+0100". */
    fun timezone(offsetMinutesEastOfUtc: Int): String {
        val sign = if (offsetMinutesEastOfUtc < 0) '-' else '+'
        val a = Math.abs(offsetMinutesEastOfUtc)
        return "%c%02d%02d".format(sign, a / 60, a % 60)
    }

    /** Body for /SetDeviceInfo. The RAC_056905_WW firmware ignores regionalCode and picks its server from Nation. */
    fun deviceInfoBody(nation: String, regionalCode: String): String =
        members(linkedMapOf("Nation" to nation, "regionalCode" to regionalCode)) + "\r\n"

    /**
     * Body for /SetDeviceConfig. keyType is only sent by the app for a hand-typed (hidden) SSID; for a
     * network picked from the scan list it is left out.
     */
    fun deviceConfigBody(ssid: String, password: String, tz: String, keyType: String? = null): String {
        val fields = linkedMapOf("HomeApSSID" to ssid, "HomeApPW" to password)
        if (keyType != null) fields["HomeApKeyType"] = keyType
        fields["TimeZone"] = tz
        return members(fields)
    }

    /** The status code from a reply, or null when there is no status line. */
    fun statusCode(reply: String): Int? =
        Regex("^HTTP/1\\.[01] (\\d{3})").find(reply)?.groupValues?.get(1)?.toInt()

    /** What the app keeps of a reply: everything from the first double quote onward, wrapped in braces. */
    fun parseMembers(reply: String): JSONObject? {
        val i = reply.indexOf('"')
        if (i == -1) return null
        return try {
            JSONObject("{" + reply.substring(i).replace(Regex("\r?\n"), "").replace(Regex(",\\s*$"), "") + "}")
        } catch (_: Exception) {
            null
        }
    }

    // JSON-encoded members, one per line like the app sends them
    private fun members(fields: Map<String, String>): String =
        fields.entries.joinToString(",\r\n") { (key, value) -> "${jsonString(key)}:${jsonString(value)}" }

    // Same output as JavaScript's JSON.stringify for a string. JSONObject.quote() would also escape
    // '/', which the appliance's parser may not expect inside a password.
    private fun jsonString(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
