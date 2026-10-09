package kittoku.osc.terminal

import org.junit.Assert.assertEquals
import org.junit.Test


class SstpHttpRequestTest {
    @Test
    fun plainRequestMatchesTheSstpSpecification() {
        val request = buildSstpDuplexPostRequest("vpn.example.com", "GUID", "")

        assertEquals(
            "SSTP_DUPLEX_POST /sra_{BA195980-CD49-458b-9E23-C84EE0ADCD75}/ HTTP/1.1\r\n" +
                "Content-Length: 18446744073709551615\r\n" +
                "Host: vpn.example.com\r\n" +
                "SSTPCORRELATIONID: {GUID}\r\n\r\n",
            request,
        )
    }

    @Test
    fun camouflageSecretGoesIntoTheQueryString() {
        val request = buildSstpDuplexPostRequest("vpn.example.com", "GUID", "6a828775")

        assertEquals(
            "SSTP_DUPLEX_POST /sra_{BA195980-CD49-458b-9E23-C84EE0ADCD75}/?6a828775 HTTP/1.1",
            request.lineSequence().first(),
        )
    }
}
