package kittoku.osc.preference

import kittoku.osc.preference.accessor.getBooleanPrefValue
import kittoku.osc.preference.accessor.getStringPrefValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class ProfileSettingsTest {
    private fun errorsOf(edit: Profile.() -> Unit) = profileSettingsErrors(Profile().apply(edit))

    @Test
    fun defaultProfileIsValid() {
        assertTrue(profileSettingsErrors(Profile()).isEmpty())
    }

    @Test
    fun ipLiterals() {
        assertTrue(isIpv4Literal("192.168.2.10"))
        assertFalse(isIpv4Literal("256.1.1.1"))
        assertFalse(isIpv4Literal("192.168.2"))
        assertFalse(isIpv4Literal("vpn.example.com"))
        assertTrue(isIpv6Literal("2001:db8::1"))
        assertTrue(isIpv6Literal("::1"))
        assertFalse(isIpv6Literal("dns.google"))
    }

    @Test
    fun customRoutesMustBeAddressSlashPrefix() {
        assertTrue(areValidRoutes("192.168.1.0/24\n 10.8.1.0/24 \n\n2001:db8::/32"))
        assertFalse(areValidRoutes(""))
        assertFalse(areValidRoutes("192.168.1.0"))
        assertFalse(areValidRoutes("192.168.1.0/33"))
        assertFalse(areValidRoutes("router.local/24"))
        assertFalse(areValidRoutes("2001:db8::/129"))
    }

    @Test
    fun routesAreNormalizedForTheCore() {
        assertEquals("192.168.1.0/24\n10.8.1.0/24", normalizeRoutes(" 192.168.1.0/24 \r\n\n10.8.1.0/24\n"))
    }

    @Test
    fun switchedOnFeaturesNeedTheirValues() {
        assertEquals(setOf(SettingsError.CUSTOM_ROUTES_INVALID), errorsOf { setBoolean(OscPrefKey.ROUTE_DO_ADD_CUSTOM_ROUTES, true) })
        assertEquals(setOf(SettingsError.CUSTOM_DNS_INVALID), errorsOf {
            setBoolean(OscPrefKey.DNS_DO_USE_CUSTOM_SERVER, true)
            setString(OscPrefKey.DNS_CUSTOM_ADDRESS, "dns.google")
        })
        assertEquals(setOf(SettingsError.SNI_EMPTY), errorsOf { setBoolean(OscPrefKey.SSL_DO_USE_CUSTOM_SNI, true) })
        assertEquals(setOf(SettingsError.SUITES_EMPTY), errorsOf { setBoolean(OscPrefKey.SSL_DO_SELECT_SUITES, true) })
        assertEquals(setOf(SettingsError.LOG_DIR_MISSING), errorsOf { setBoolean(OscPrefKey.LOG_DO_SAVE_LOG, true) })
        assertEquals(setOf(SettingsError.STATIC_IPV4_INVALID), errorsOf { setBoolean(OscPrefKey.PPP_DO_REQUEST_STATIC_IPv4_ADDRESS, true) })
    }

    @Test
    fun proxyChecksItsOwnHostname() {
        // the legacy checkPreferences() looked at HOME_HOSTNAME here and let an empty proxy host through
        assertEquals(setOf(SettingsError.PROXY_HOST_EMPTY), errorsOf {
            setBoolean(OscPrefKey.PROXY_DO_USE_PROXY, true)
            setString(OscPrefKey.HOME_HOSTNAME, "vpn.example.com")
        })
    }

    @Test
    fun customCertificatesNeedAFolderAndAVersion() {
        assertEquals(
            setOf(SettingsError.TLS_VERSION_REQUIRED, SettingsError.CERT_DIR_MISSING),
            errorsOf { setBoolean(OscPrefKey.SSL_DO_SPECIFY_CERT, true) },
        )
        assertTrue(errorsOf {
            setBoolean(OscPrefKey.SSL_DO_SPECIFY_CERT, true)
            setString(OscPrefKey.SSL_VERSION, "TLSv1.3")
            setUri(OscPrefKey.SSL_CERT_DIR, "content://certs")
        }.isEmpty())
    }

    @Test
    fun numbersAndProtocols() {
        assertEquals(setOf(SettingsError.MTU_INVALID), errorsOf { setInt(OscPrefKey.PPP_MTU, 20) })
        assertEquals(setOf(SettingsError.AUTH_EMPTY), errorsOf { setSet(OscPrefKey.PPP_AUTH_PROTOCOLS, emptySet()) })
        assertEquals(setOf(SettingsError.NO_NETWORK_PROTOCOL), errorsOf { setBoolean(OscPrefKey.PPP_IPv4_ENABLED, false) })
    }

    @Test
    fun hiddenInvalidValuesAreResetInsteadOfBlockingTheSave() {
        val profile = Profile().apply {
            setInt(OscPrefKey.RECONNECTION_COUNT, -1)
            setInt(OscPrefKey.PROXY_PORT, -1)
            setString(OscPrefKey.PROXY_HOSTNAME, "  proxy.local ")
        }

        sanitizeProfile(profile)

        assertEquals(DEFAULT_INT_MAP.getValue(OscPrefKey.RECONNECTION_COUNT), profile.getInt(OscPrefKey.RECONNECTION_COUNT))
        assertEquals(DEFAULT_INT_MAP.getValue(OscPrefKey.PROXY_PORT), profile.getInt(OscPrefKey.PROXY_PORT))
        assertEquals("proxy.local", profile.getString(OscPrefKey.PROXY_HOSTNAME))
        assertTrue(profileSettingsErrors(profile).isEmpty())
    }

    @Test
    fun visibleInvalidValuesAreKeptAndReported() {
        val profile = Profile().apply {
            setBoolean(OscPrefKey.RECONNECTION_ENABLED, true)
            setInt(OscPrefKey.RECONNECTION_COUNT, -1)
        }

        sanitizeProfile(profile)

        assertEquals(setOf(SettingsError.RECONNECTION_COUNT_INVALID), profileSettingsErrors(profile))
    }

    @Test
    fun savingAFullProfileAppliesAdvancedSettingsToTheActiveConnection() {
        val prefs = InMemorySharedPreferences()
        val store = ProfileStore(prefs)
        store.save(null, ProfileForm(name = "Home", hostname = "home.example.com"))

        val profile = store.read("Home")!!.apply {
            setBoolean(OscPrefKey.ROUTE_DO_ADD_DEFAULT_ROUTE, false)
            setString(OscPrefKey.ROUTE_CUSTOM_ROUTES, "192.168.1.0/24")
        }
        store.saveProfile("Home", "Home", profile)

        assertFalse(getBooleanPrefValue(OscPrefKey.ROUTE_DO_ADD_DEFAULT_ROUTE, prefs))
        assertEquals("192.168.1.0/24", getStringPrefValue(OscPrefKey.ROUTE_CUSTOM_ROUTES, prefs))
    }
}
