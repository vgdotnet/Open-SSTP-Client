package kittoku.osc.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream


class HttpResponseTest {
    private fun stream(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.US_ASCII))

    @Test
    fun readsTheHeadAndStopsAtTheEmptyLine() {
        val input = stream("HTTP/1.1 200 Connection established\r\nProxy-Agent: test\r\n\r\nBODY")

        assertEquals("HTTP/1.1 200 Connection established\r\nProxy-Agent: test\r\n\r\n", readHttpResponseHead(input))
        assertEquals('B'.code, input.read())
    }

    @Test
    fun streamClosedBeforeTheHeadEndsIsReportedNotLoopedOn() {
        // the previous implementation spun forever on read() == -1
        assertNull(readHttpResponseHead(stream("HTTP/1.1 403 Forbidden\r\n")))
        assertNull(readHttpResponseHead(stream("")))
    }

    @Test
    fun oversizedHeadIsRejected() {
        assertNull(readHttpResponseHead(stream("X".repeat(100)), maxSize = 50))
    }
}
