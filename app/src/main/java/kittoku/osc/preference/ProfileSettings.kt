package kittoku.osc.preference

import kittoku.osc.MAX_MRU
import kittoku.osc.MAX_MTU
import kittoku.osc.MIN_MRU
import kittoku.osc.MIN_MTU


internal enum class SettingsSection { ROUTES, APPS, ADVANCED }

internal enum class SettingsError(val section: SettingsSection) {
    CUSTOM_ROUTES_INVALID(SettingsSection.ROUTES),
    CUSTOM_DNS_INVALID(SettingsSection.ROUTES),
    TLS_VERSION_REQUIRED(SettingsSection.ADVANCED),
    CERT_DIR_MISSING(SettingsSection.ADVANCED),
    SUITES_EMPTY(SettingsSection.ADVANCED),
    SNI_EMPTY(SettingsSection.ADVANCED),
    PROXY_HOST_EMPTY(SettingsSection.ADVANCED),
    PROXY_PORT_INVALID(SettingsSection.ADVANCED),
    MRU_INVALID(SettingsSection.ADVANCED),
    MTU_INVALID(SettingsSection.ADVANCED),
    AUTH_EMPTY(SettingsSection.ADVANCED),
    AUTH_TIMEOUT_INVALID(SettingsSection.ADVANCED),
    NO_NETWORK_PROTOCOL(SettingsSection.ADVANCED),
    STATIC_IPV4_INVALID(SettingsSection.ADVANCED),
    RECONNECTION_COUNT_INVALID(SettingsSection.ADVANCED),
    RECONNECTION_INTERVAL_INVALID(SettingsSection.ADVANCED),
    LOG_DIR_MISSING(SettingsSection.ADVANCED),
    APPS_ONLY_EMPTY(SettingsSection.APPS),
}

internal val PORT_RANGE = 1..65535
internal val MRU_RANGE = MIN_MRU..MAX_MRU
internal val MTU_RANGE = MIN_MTU..MAX_MTU
internal val AUTH_TIMEOUT_RANGE = 1..600
internal val RECONNECTION_COUNT_RANGE = 1..100
internal val RECONNECTION_INTERVAL_RANGE = 0..3600

internal const val SSL_VERSION_DEFAULT = "DEFAULT"

private val IPV4_LITERAL = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
private val IPV6_LITERAL = Regex("""^[0-9A-Fa-f:.]{2,45}$""")

internal fun isIpv4Literal(text: String): Boolean {
    val match = IPV4_LITERAL.matchEntire(text) ?: return false
    return match.groupValues.drop(1).all { it.toInt() <= 255 }
}

internal fun isIpv6Literal(text: String): Boolean = IPV6_LITERAL.matches(text) && text.count { it == ':' } >= 2

internal fun isIpLiteral(text: String): Boolean = isIpv4Literal(text) || isIpv6Literal(text)

// IPTerminal splits the routes by '\n' and does not trim, so the stored text must be clean
internal fun normalizeRoutes(text: String): String =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

internal fun areValidRoutes(text: String): Boolean {
    val routes = normalizeRoutes(text).lines().filter { it.isNotEmpty() }
    if (routes.isEmpty()) return false

    return routes.all { route ->
        val parts = route.split("/")
        val prefix = parts.getOrNull(1)?.toIntOrNull()

        when {
            parts.size != 2 || prefix == null -> false
            isIpv4Literal(parts[0]) -> prefix in 0..32
            isIpv6Literal(parts[0]) -> prefix in 0..128
            else -> false
        }
    }
}

// Values of switched-off features cannot be seen in the editor, so they must not block saving
internal fun sanitizeProfile(profile: Profile) {
    fun resetIfOutOf(key: OscPrefKey, range: IntRange) {
        if (profile.getInt(key) !in range) profile.setInt(key, DEFAULT_INT_MAP.getValue(key))
    }

    if (!profile.getBoolean(OscPrefKey.PROXY_DO_USE_PROXY)) {
        resetIfOutOf(OscPrefKey.PROXY_PORT, PORT_RANGE)
    }

    if (!profile.getBoolean(OscPrefKey.RECONNECTION_ENABLED)) {
        resetIfOutOf(OscPrefKey.RECONNECTION_COUNT, RECONNECTION_COUNT_RANGE)
        resetIfOutOf(OscPrefKey.RECONNECTION_INTERVAL, RECONNECTION_INTERVAL_RANGE)
    }

    profile.setString(OscPrefKey.ROUTE_CUSTOM_ROUTES, normalizeRoutes(profile.getString(OscPrefKey.ROUTE_CUSTOM_ROUTES)))

    listOf(
        OscPrefKey.DNS_CUSTOM_ADDRESS,
        OscPrefKey.PPP_STATIC_IPv4_ADDRESS,
        OscPrefKey.SSL_CUSTOM_SNI,
        OscPrefKey.PROXY_HOSTNAME,
    ).forEach { profile.setString(it, profile.getString(it).trim()) }
}

internal fun profileSettingsErrors(profile: Profile): Set<SettingsError> {
    val errors = mutableSetOf<SettingsError>()
    fun check(failed: Boolean, error: SettingsError) {
        if (failed) errors.add(error)
    }

    with(profile) {
        check(
            getBoolean(OscPrefKey.ROUTE_DO_ADD_CUSTOM_ROUTES) && !areValidRoutes(getString(OscPrefKey.ROUTE_CUSTOM_ROUTES)),
            SettingsError.CUSTOM_ROUTES_INVALID,
        )
        check(
            getBoolean(OscPrefKey.DNS_DO_USE_CUSTOM_SERVER) && !isIpLiteral(getString(OscPrefKey.DNS_CUSTOM_ADDRESS).trim()),
            SettingsError.CUSTOM_DNS_INVALID,
        )

        // the core builds SSLContext.getInstance(version) when custom certificates are used
        val doSpecifyCert = getBoolean(OscPrefKey.SSL_DO_SPECIFY_CERT)
        check(doSpecifyCert && getString(OscPrefKey.SSL_VERSION) == SSL_VERSION_DEFAULT, SettingsError.TLS_VERSION_REQUIRED)
        check(doSpecifyCert && getUri(OscPrefKey.SSL_CERT_DIR).isEmpty(), SettingsError.CERT_DIR_MISSING)
        check(getBoolean(OscPrefKey.SSL_DO_SELECT_SUITES) && getSet(OscPrefKey.SSL_SUITES).isEmpty(), SettingsError.SUITES_EMPTY)
        check(getBoolean(OscPrefKey.SSL_DO_USE_CUSTOM_SNI) && getString(OscPrefKey.SSL_CUSTOM_SNI).isBlank(), SettingsError.SNI_EMPTY)

        if (getBoolean(OscPrefKey.PROXY_DO_USE_PROXY)) {
            check(getString(OscPrefKey.PROXY_HOSTNAME).isBlank(), SettingsError.PROXY_HOST_EMPTY)
            check(getInt(OscPrefKey.PROXY_PORT) !in PORT_RANGE, SettingsError.PROXY_PORT_INVALID)
        }

        check(getInt(OscPrefKey.PPP_MRU) !in MRU_RANGE, SettingsError.MRU_INVALID)
        check(getInt(OscPrefKey.PPP_MTU) !in MTU_RANGE, SettingsError.MTU_INVALID)
        check(getSet(OscPrefKey.PPP_AUTH_PROTOCOLS).isEmpty(), SettingsError.AUTH_EMPTY)
        check(getInt(OscPrefKey.PPP_AUTH_TIMEOUT) !in AUTH_TIMEOUT_RANGE, SettingsError.AUTH_TIMEOUT_INVALID)

        val isIpv4Enabled = getBoolean(OscPrefKey.PPP_IPv4_ENABLED)
        check(!isIpv4Enabled && !getBoolean(OscPrefKey.PPP_IPv6_ENABLED), SettingsError.NO_NETWORK_PROTOCOL)
        check(
            isIpv4Enabled && getBoolean(OscPrefKey.PPP_DO_REQUEST_STATIC_IPv4_ADDRESS) &&
                !isIpv4Literal(getString(OscPrefKey.PPP_STATIC_IPv4_ADDRESS).trim()),
            SettingsError.STATIC_IPV4_INVALID,
        )

        check(getInt(OscPrefKey.RECONNECTION_COUNT) !in RECONNECTION_COUNT_RANGE, SettingsError.RECONNECTION_COUNT_INVALID)
        check(getInt(OscPrefKey.RECONNECTION_INTERVAL) !in RECONNECTION_INTERVAL_RANGE, SettingsError.RECONNECTION_INTERVAL_INVALID)
        check(getBoolean(OscPrefKey.LOG_DO_SAVE_LOG) && getUri(OscPrefKey.LOG_DIR).isEmpty(), SettingsError.LOG_DIR_MISSING)

        // Android routes every app through the VPN when no allowed app is given, the opposite of "only selected"
        check(isOnlySelectedApps() && getSet(OscPrefKey.ROUTE_SELECTED_APPS).isEmpty(), SettingsError.APPS_ONLY_EMPTY)
    }

    return errors
}

internal fun Profile.isOnlySelectedApps(): Boolean =
    getBoolean(OscPrefKey.ROUTE_DO_ENABLE_APP_BASED_RULE) && getString(OscPrefKey.ROUTE_APP_LIST_TYPE) == LIST_TYPE_ALLOWED

// Returns true when something was removed
internal fun pruneUninstalledApps(profile: Profile, isInstalled: (String) -> Boolean): Boolean {
    val selected = profile.getSet(OscPrefKey.ROUTE_SELECTED_APPS)
    val installed = selected.filter(isInstalled).toSet()
    if (installed.size == selected.size) return false

    profile.setSet(OscPrefKey.ROUTE_SELECTED_APPS, installed)
    return true
}
