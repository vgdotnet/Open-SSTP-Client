package kittoku.osc.preference

import kittoku.osc.preference.accessor.setBooleanPrefValue
import kittoku.osc.preference.accessor.setIntPrefValue
import kittoku.osc.preference.accessor.setSetPrefValue
import kittoku.osc.preference.accessor.setStringPrefValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


class ConnectionReadinessTest {
    private val prefs = InMemorySharedPreferences()

    private fun withServer() = setStringPrefValue("vpn.example.com", OscPrefKey.HOME_HOSTNAME, prefs)

    @Test
    fun emptyServerIsReportedFirst() {
        assertEquals(ReadinessError.NO_SERVER, connectionReadiness(prefs) { true })
    }

    @Test
    fun defaultsWithAServerAreReady() {
        withServer()

        assertNull(connectionReadiness(prefs) { true })
    }

    @Test
    fun portOutOfRange() {
        withServer()
        setIntPrefValue(0, OscPrefKey.SSL_PORT, prefs)

        assertEquals(ReadinessError.INVALID_SETTINGS, connectionReadiness(prefs) { true })
    }

    @Test
    fun proxyWithoutItsOwnHostIsInvalid() {
        withServer()
        setBooleanPrefValue(true, OscPrefKey.PROXY_DO_USE_PROXY, prefs)

        assertEquals(ReadinessError.INVALID_SETTINGS, connectionReadiness(prefs) { true })

        setStringPrefValue("proxy.example.com", OscPrefKey.PROXY_HOSTNAME, prefs)
        assertNull(connectionReadiness(prefs) { true })
    }

    @Test
    fun mtuOutOfRangeIsInvalid() {
        withServer()
        setIntPrefValue(10, OscPrefKey.PPP_MTU, prefs)

        assertEquals(ReadinessError.INVALID_SETTINGS, connectionReadiness(prefs) { true })
    }

    @Test
    fun hiddenInvalidValuesDoNotBlockTheConnection() {
        withServer()
        setIntPrefValue(-1, OscPrefKey.RECONNECTION_COUNT, prefs)

        assertNull(connectionReadiness(prefs) { true })
    }

    @Test
    fun onlySelectedAppsWithNothingInstalledIsRefused() {
        withServer()
        setBooleanPrefValue(true, OscPrefKey.ROUTE_DO_ENABLE_APP_BASED_RULE, prefs)
        setStringPrefValue(LIST_TYPE_ALLOWED, OscPrefKey.ROUTE_APP_LIST_TYPE, prefs)
        setSetPrefValue(setOf("org.removed.app"), OscPrefKey.ROUTE_SELECTED_APPS, prefs)

        assertEquals(ReadinessError.NO_APPS_SELECTED, connectionReadiness(prefs) { false })
        assertNull(connectionReadiness(prefs) { it == "org.removed.app" })
    }

    @Test
    fun allExceptSelectedWithNothingInstalledIsFine() {
        withServer()
        setBooleanPrefValue(true, OscPrefKey.ROUTE_DO_ENABLE_APP_BASED_RULE, prefs)
        setStringPrefValue(LIST_TYPE_DISALLOWED, OscPrefKey.ROUTE_APP_LIST_TYPE, prefs)
        setSetPrefValue(setOf("org.removed.app"), OscPrefKey.ROUTE_SELECTED_APPS, prefs)

        assertNull(connectionReadiness(prefs) { false })
    }
}
