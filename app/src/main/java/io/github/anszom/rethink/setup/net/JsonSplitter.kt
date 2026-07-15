package io.github.anszom.rethink.setup.net

import java.io.ByteArrayOutputStream

/**
 * Byte-level JSON message framer, ported from Rethink `util/json_splitter.ts`.
 *
 * ThinQ2 setup messages arrive back-to-back with no length prefix, so we split them
 * by tracking brace/bracket depth while ignoring tokens inside string literals.
 */
class JsonSplitter {
    private var state = 0 // 0 = normal, 1 = in string, 2 = escape
    private var depth = 0
    private val buf = ByteArrayOutputStream()

    fun feed(byte: Int, onMessage: (String) -> Unit) {
        buf.write(byte)

        when (state) {
            0 -> {
                if (byte == 0x5b || byte == 0x7b) { // [ {
                    depth++
                } else if (byte == 0x5d || byte == 0x7d) { // ] }
                    depth--
                    if (depth < 0) throw IllegalStateException("Invalid JSON: too many closing tokens")
                    if (depth == 0) {
                        onMessage(buf.toString("UTF-8"))
                        buf.reset()
                    }
                } else if (byte == 0x22) { // "
                    state = 1
                }
            }
            1 -> {
                if (byte == 0x22) state = 0 // "
                else if (byte == 0x5c) state = 2 // backslash
            }
            2 -> state = 1
        }
    }
}
