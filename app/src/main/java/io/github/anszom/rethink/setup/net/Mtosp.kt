package io.github.anszom.rethink.setup.net

import java.io.InputStream

/**
 * ThinQ1 "mTosp" framing, ported from Rethink `util/mtosp.ts`.
 *
 * Frame layout:
 *   0xaa | length (16-bit big-endian) | payload (xml) | crc16 (16-bit big-endian) | 0xbb
 */
object Mtosp {

    fun format(xml: String): ByteArray {
        val payload = xml.toByteArray(Charsets.UTF_8)
        val header = byteArrayOf(0xaa.toByte(), (payload.size ushr 8).toByte(), (payload.size and 0xff).toByte())
        val crc = crc16(header + payload)
        val trailer = byteArrayOf((crc ushr 8).toByte(), (crc and 0xff).toByte(), 0xbb.toByte())
        return header + payload + trailer
    }

    /** Reads a single mTosp frame from [input] and returns the XML payload. Throws on malformed data. */
    fun readFrame(input: InputStream): String {
        val start = readByte(input)
        if (start != 0xaa) throw IllegalStateException("invalid header byte: 0x${start.toString(16)}")

        val lenHi = readByte(input)
        val lenLo = readByte(input)
        val length = (lenHi shl 8) or lenLo

        val payload = ByteArray(length)
        readFully(input, payload)

        val crcHi = readByte(input)
        val crcLo = readByte(input)
        val readCrc = (crcHi shl 8) or crcLo

        val trailer = readByte(input)
        if (trailer != 0xbb) throw IllegalStateException("invalid trailer byte: 0x${trailer.toString(16)}")

        val header = byteArrayOf(0xaa.toByte(), lenHi.toByte(), lenLo.toByte())
        val computed = crc16(header + payload)
        if (computed != readCrc) throw IllegalStateException("invalid checksum")

        return String(payload, Charsets.UTF_8)
    }

    private fun readByte(input: InputStream): Int {
        val b = input.read()
        if (b < 0) throw java.io.EOFException("connection closed")
        return b and 0xff
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw java.io.EOFException("connection closed")
            off += n
        }
    }
}
