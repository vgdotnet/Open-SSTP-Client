package kittoku.osc.preference

import kittoku.osc.preference.accessor.setBooleanPrefValue
import kittoku.osc.preference.accessor.setIntPrefValue
import kittoku.osc.preference.accessor.setStringPrefValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


class CheckPreferencesTest {
    private val prefs = InMemorySharedPreferences().also {
        setStringPrefValue("vpn.example.com", OscPrefKey.HOME_HOSTNAME, it)
    }

    @Test
    fun defaultsWithAHostnameAreValid() {
        assertNull(checkPreferences(prefs))
    }

    @Test
    fun proxyHostnameIsCheckedNotTheVpnHostname() {
        setBooleanPrefValue(true, OscPrefKey.PROXY_DO_USE_PROXY, prefs)

        assertEquals("Proxy server hostname is missing", checkPreferences(prefs))

        setStringPrefValue("proxy.example.com", OscPrefKey.PROXY_HOSTNAME, prefs)
        assertNull(checkPreferences(prefs))
    }

    @Test
    fun mtuMessageNamesMtu() {
        setIntPrefValue(10, OscPrefKey.PPP_MTU, prefs)

        assertEquals("The given MTU is out of 68-2000", checkPreferences(prefs))
    }
}
