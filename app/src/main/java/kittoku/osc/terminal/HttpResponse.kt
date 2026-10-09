package kittoku.osc.terminal

import java.io.ByteArrayOutputStream
import java.io.InputStream


internal const val MAX_HTTP_HEAD_SIZE = 16 * 1024

// Reads the HTTP head up to and including the empty line; null when the stream ends first or the head is too large
internal fun readHttpResponseHead(input: InputStream, maxSize: Int = MAX_HTTP_HEAD_SIZE): String? {
    val bytes = ByteArrayOutputStream()
    var tail = 0

    while (bytes.size() < maxSize) {
        val b = input.read()
        if (b < 0) return null

        bytes.write(b)
        tail = (tail shl 8 or b) and 0xFFFFFFFF.toInt()
        if (tail == 0x0D0A0D0A) return String(bytes.toByteArray(), Charsets.US_ASCII)
    }

    return null
}
