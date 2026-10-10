package kittoku.osc.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test


class StatusSummaryTest {
    private val keeneticStatus = """
        [SSL/TLS Parameters]
        PROTOCOL: TLSv1.3
        SUITE: TLS_CHACHA20_POLY1305_SHA256

        [Assigned IP Address]
        192.168.2.2

        [DNS Server Address]
        192.168.2.1

        [Routing]
        0.0.0.0/0 -> 0.0.0.0 tun0 mtu 0
        ::/0 unreachable mtu 0
        192.168.2.2/32 -> 0.0.0.0 tun0 mtu 0
    """.trimIndent()

    @Test
    fun parsesEverySection() {
        val summary = parseStatusSummary(keeneticStatus)!!

        assertEquals("TLSv1.3", summary.tlsProtocol)
        assertEquals("TLS_CHACHA20_POLY1305_SHA256", summary.cipherSuite)
        assertEquals(listOf("192.168.2.2"), summary.addresses)
        assertEquals(listOf("192.168.2.1"), summary.dnsServers)
        assertEquals(3, summary.routes.size)
        assertTrue(summary.hasDefaultRoute)
        assertNull(summary.appListType)
        assertTrue(summary.apps.isEmpty())
    }

    @Test
    fun blankTextMeansNoConnection() {
        assertNull(parseStatusSummary(""))
        assertNull(parseStatusSummary("  \n "))
    }

    @Test
    fun unspecifiedDnsIsEmpty() {
        val summary = parseStatusSummary("[DNS Server Address]\nNot specified\n")!!

        assertTrue(summary.dnsServers.isEmpty())
    }

    @Test
    fun appListSectionIsRecognized() {
        val summary = parseStatusSummary("[Routing]\n10.0.0.0/8 -> 0.0.0.0 tun0 mtu 0\n\n[Allowed Apps]\nGmail\nChrome\n")!!

        assertEquals("Allowed Apps", summary.appListType)
        assertEquals(listOf("Gmail", "Chrome"), summary.apps)
        assertFalse(summary.hasDefaultRoute)
    }

    @Test
    fun appRuleIsRecognizedFromTheCoreSectionName() {
        assertEquals(AppRule.ONLY, parseStatusSummary("[Allowed Apps]\nGmail\n")!!.appRule)
        assertEquals(AppRule.EXCEPT, parseStatusSummary("[Disallowed Apps]\nGmail\n")!!.appRule)
        assertNull(parseStatusSummary(keeneticStatus)!!.appRule)
    }
}

