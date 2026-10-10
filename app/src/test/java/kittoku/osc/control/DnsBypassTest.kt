package kittoku.osc.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress


class DnsBypassTest {
    private fun ByteArrayOutputStream.short(value: Int) {
        write(value ushr 8)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.name(name: String) {
        name.split('.').forEach { label ->
            write(label.length)
            write(label.toByteArray(Charsets.US_ASCII))
        }
        write(0)
    }

    // A response for www.ozon.ru: CNAME to a CDN name, then two A records on the CNAME target (compression pointers)
    private fun ozonResponse(rcode: Int = 0, isResponse: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        out.short(0x1234)
        out.short((if (isResponse) 0x8180 else 0x0100) or rcode)
        out.short(1) // questions
        out.short(3) // answers
        out.short(0)
        out.short(0)

        out.name("www.ozon.ru") // offset 12
        out.short(DNS_TYPE_A)
        out.short(1)

        // answer 1: www.ozon.ru CNAME cdn.ozonusercontent.com
        out.short(0xC00C)
        out.short(5)
        out.short(1)
        out.write(byteArrayOf(0, 0, 0, 60))
        val target = ByteArrayOutputStream().also { it.name("cdn.ozonusercontent.com") }.toByteArray()
        out.short(target.size)
        val targetOffset = out.size()
        out.write(target)

        // answers 2 and 3: A records on the CNAME target
        listOf(byteArrayOf(185.toByte(), 15, 8, 1), byteArrayOf(185.toByte(), 15, 8, 2)).forEach { address ->
            out.short(0xC000 or targetOffset)
            out.short(DNS_TYPE_A)
            out.short(1)
            out.write(byteArrayOf(0, 0, 0, 60))
            out.short(4)
            out.write(address)
        }

        return out.toByteArray()
    }

    private fun udpPacket(payload: ByteArray, sourcePort: Int = 53, protocol: Int = 17): ByteArray {
        val out = ByteArrayOutputStream()
        val total = 20 + 8 + payload.size
        out.write(0x45)
        out.write(0)
        out.short(total)
        out.short(0)
        out.short(0x4000) // DF, no fragments
        out.write(64)
        out.write(protocol)
        out.short(0)
        out.write(byteArrayOf(192.toByte(), 168.toByte(), 2, 1))
        out.write(byteArrayOf(192.toByte(), 168.toByte(), 2, 2))
        out.short(sourcePort)
        out.short(40000)
        out.short(8 + payload.size)
        out.short(0)
        out.write(payload)
        return out.toByteArray()
    }

    @Test
    fun locatesTheDnsPayloadInAnIpv4UdpPacket() {
        val payload = ozonResponse()
        val packet = udpPacket(payload)

        val range = dnsPayloadRange(packet, 0, packet.size)!!

        assertEquals(28, range.first)
        assertEquals(packet.size - 1, range.last)
        assertNull(dnsPayloadRange(udpPacket(payload, sourcePort = 443), 0, packet.size))
        assertNull(dnsPayloadRange(udpPacket(payload, protocol = 6), 0, packet.size))
        assertNull(dnsPayloadRange(packet, 0, 20))
    }

    @Test
    fun payloadRangeHonoursAnOffsetIntoALargerBuffer() {
        val packet = udpPacket(ozonResponse())
        val buffer = ByteArray(8) + packet + ByteArray(3)

        val range = dnsPayloadRange(buffer, 8, packet.size)!!

        assertEquals(8 + 28, range.first)
    }

    @Test
    fun parsesQuestionsAndAddressesThroughCnameAndCompression() {
        val response = parseDnsResponse(ozonResponse(), ozonResponse().indices)!!

        assertEquals(listOf("www.ozon.ru"), response.questions)
        assertEquals(2, response.answers.size)
        assertEquals("cdn.ozonusercontent.com", response.answers[0].name)
        assertEquals(DNS_TYPE_A, response.answers[0].type)
        assertEquals("185.15.8.2", InetAddress.getByAddress(response.answers[1].address).hostAddress)
    }

    @Test
    fun rejectsQueriesErrorsAndTruncatedMessages() {
        val payload = ozonResponse()

        assertNull(parseDnsResponse(ozonResponse(isResponse = false), payload.indices))
        assertNull(parseDnsResponse(ozonResponse(rcode = 3), payload.indices))
        assertNull(parseDnsResponse(payload, 0 until payload.size - 5))
        assertNull(parseDnsResponse(ByteArray(5), 0 until 5))
    }

    @Test
    fun compressionPointerLoopDoesNotHang() {
        val out = ByteArrayOutputStream()
        out.short(1)
        out.short(0x8180)
        out.short(1)
        out.short(0)
        out.short(0)
        out.short(0)
        out.short(0xC00C) // the question name points at itself

        assertNull(parseDnsResponse(out.toByteArray(), out.toByteArray().indices))
    }

    @Test
    fun domainRulesMatchSubdomainsOnly() {
        val domains = listOf("ozon.ru", "sberbank.ru")

        assertEquals("ozon.ru", matchesDomain("ozon.ru", domains))
        assertEquals("ozon.ru", matchesDomain("www.ozon.ru", domains))
        assertEquals("ozon.ru", matchesDomain("api.m.ozon.ru", domains))
        assertNull(matchesDomain("notozon.ru", domains))
        assertNull(matchesDomain("ozon.ru.evil.com", domains))
    }

    @Test
    fun domainListNormalizationAndValidation() {
        assertEquals("ozon.ru\nsberbank.ru", normalizeDomains(" *.Ozon.ru \n\n.sberbank.ru.\nozon.ru"))
        assertTrue(areValidDomains("ozon.ru\nxn--80ak6aa92e.com"))
        assertFalse(areValidDomains(""))
        assertFalse(areValidDomains("https://ozon.ru"))
        assertFalse(areValidDomains("ozon.ru/path"))
        assertFalse(areValidDomains("localhost"))
        assertFalse(areValidDomains("ozon ru"))
    }

    @Test
    fun decisionReturnsOnlyNewAddressesForMatchedQuestions() {
        val response = parseDnsResponse(ozonResponse(), ozonResponse().indices)!!
        val known = InetAddress.getByName("185.15.8.1")

        val decision = decideBypass(response, listOf("ozon.ru")) { it == known }!!
        assertEquals("ozon.ru", decision.matchedDomain)
        assertEquals(listOf(InetAddress.getByName("185.15.8.2")), decision.addresses)

        assertNull(decideBypass(response, listOf("ozon.ru")) { true })
        assertNull(decideBypass(response, listOf("wildberries.ru")) { false })
        assertNotNull(decideBypass(response, listOf("OZON.RU").map(::normalizeDomain)) { false })
    }
}
