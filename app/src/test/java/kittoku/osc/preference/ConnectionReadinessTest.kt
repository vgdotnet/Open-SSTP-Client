package kittoku.osc.preference

import kittoku.osc.preference.accessor.setBooleanPrefValue
import kittoku.osc.preference.accessor.setIntPrefValue
import kittoku.osc.preference.accessor.setStringPrefValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


class ConnectionReadinessTest {
    private val prefs = InMemorySharedPreferences()

    private fun withServer() = setStringPrefValue("vpn.example.com", OscPrefKey.HOME_HOSTNAME, prefs)

    @Test
    fun emptyServerIsReportedFirst() {
        assertEquals(ReadinessError.NO_SERVER, connectionReadiness(prefs))
    }

    @Test
    fun defaultsWithAServerAreReady() {
        withServer()

        assertNull(connectionReadiness(prefs))
    }

    @Test
    fun portOutOfRange() {
        withServer()
        setIntPrefValue(0, OscPrefKey.SSL_PORT, prefs)

        assertEquals(ReadinessError.INVALID_SETTINGS, connectionReadiness(prefs))
    }

    @Test
    fun proxyWithoutItsOwnHostIsInvalid() {
        withServer()
        setBooleanPrefValue(true, OscPrefKey.PROXY_DO_USE_PROXY, prefs)

        assertEquals(ReadinessError.INVALID_SETTINGS, connectionReadiness(prefs))

        setStringPrefValue("proxy.example.com", OscPrefKey.PROXY_HOSTNAME, prefs)
        assertNull(connectionReadiness(prefs))
    }

    @Test
    fun mtuOutOfRangeIsInvalid() {
        withServer()
        setIntPrefValue(10, OscPrefKey.PPP_MTU, prefs)

        assertEquals(ReadinessError.INVALID_SETTINGS, connectionReadiness(prefs))
    }

    @Test
    fun hiddenInvalidValuesDoNotBlockTheConnection() {
        withServer()
        setIntPrefValue(-1, OscPrefKey.RECONNECTION_COUNT, prefs)

        assertNull(connectionReadiness(prefs))
    }
}
