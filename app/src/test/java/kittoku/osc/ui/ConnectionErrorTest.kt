package kittoku.osc.ui

import org.junit.Assert.assertEquals
import org.junit.Test


class ConnectionErrorTest {
    @Test
    fun authenticationFailuresAreCredentials() {
        assertEquals(ErrorKind.CREDENTIALS, classifyError("MSCHAPV2: ERR_AUTHENTICATION_FAILED"))
        assertEquals(ErrorKind.CREDENTIALS, classifyError("MSCHAPV2: ERR_VERIFICATION_FAILED"))
        assertEquals(ErrorKind.CREDENTIALS, classifyError("PAP: ERR_AUTHENTICATION_FAILED"))
        assertEquals(ErrorKind.CREDENTIALS, classifyError("EAP: ERR_AUTHENTICATION_FAILED"))
    }

    @Test
    fun certificateProblems() {
        assertEquals(ErrorKind.CERTIFICATE, classifyError("CERT_PATH: ERR_VERIFICATION_FAILED"))
        assertEquals(ErrorKind.CERTIFICATE, classifyError("CERT: ERR_PARSING_FAILED"))
        assertEquals(ErrorKind.CERTIFICATE, classifyError("SSL: ERR_VERIFICATION_FAILED"))
    }

    @Test
    fun transportProblems() {
        assertEquals(ErrorKind.HOST_UNREACHABLE, classifyError("SSL: ERR_TIMEOUT"))
        assertEquals(ErrorKind.NOT_SSTP_SERVER, classifyError("SSL: ERR_UNEXPECTED_MESSAGE"))
        assertEquals(ErrorKind.NOT_SSTP_SERVER, classifyError("SSTP_REQUEST: ERR_COUNT_EXHAUSTED"))
        assertEquals(ErrorKind.PROXY, classifyError("PROXY: ERR_AUTHENTICATION_FAILED"))
    }

    @Test
    fun serverSideDisconnects() {
        assertEquals(ErrorKind.SERVER_CLOSED, classifyError("SSTP_CONTROL: ERR_DISCONNECT_REQUESTED"))
        assertEquals(ErrorKind.SERVER_CLOSED, classifyError("SSTP_REQUEST: ERR_ABORT_REQUESTED"))
        assertEquals(ErrorKind.SERVER_CLOSED, classifyError("PPP: ERR_TERMINATE_REQUESTED"))
        assertEquals(ErrorKind.TIMEOUT, classifyError("SSTP_CONTROL: ERR_TIMEOUT"))
        assertEquals(ErrorKind.TIMEOUT, classifyError("PPP: ERR_TIMEOUT"))
    }

    @Test
    fun everythingElseIsUnknown() {
        assertEquals(ErrorKind.UNKNOWN, classifyError("OSC: ERR_UNEXPECTED"))
        assertEquals(ErrorKind.UNKNOWN, classifyError("LCP: ERR_COUNT_EXHAUSTED"))
        assertEquals(ErrorKind.UNKNOWN, classifyError(""))
        assertEquals(ErrorKind.UNKNOWN, classifyError("garbage"))
    }

    @Test
    fun elapsedTimeFormat() {
        assertEquals("00:00:00", formatElapsed(0))
        assertEquals("00:14:22", formatElapsed((14 * 60 + 22) * 1_000L))
        assertEquals("01:00:05", formatElapsed((3600 + 5) * 1_000L))
        assertEquals("00:00:00", formatElapsed(-5_000))
    }
}
