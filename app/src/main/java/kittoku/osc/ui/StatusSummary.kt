package kittoku.osc.ui

import kittoku.osc.preference.LIST_TYPE_ALLOWED
import kittoku.osc.preference.LIST_TYPE_DISALLOWED


internal data class StatusSummary(
    val tlsProtocol: String?,
    val cipherSuite: String?,
    val addresses: List<String>,
    val dnsServers: List<String>,
    val routes: List<String>,
    val appListType: String?,
    val apps: List<String>,
) {
    val hasDefaultRoute: Boolean
        get() = routes.any { it.startsWith("0.0.0.0/0 ") || it.startsWith("::/0 ") }
}

private const val SECTION_TLS = "SSL/TLS Parameters"
private const val SECTION_ADDRESS = "Assigned IP Address"
private const val SECTION_DNS = "DNS Server Address"
private const val SECTION_ROUTING = "Routing"
private const val DNS_NOT_SPECIFIED = "Not specified"

// Parses the text that NetworkObserver writes into HOME_STATUS: "[Section]" headers, one value per line
internal fun parseStatusSummary(text: String): StatusSummary? {
    if (text.isBlank()) return null

    val sections = linkedMapOf<String, MutableList<String>>()
    var current: MutableList<String>? = null

    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEach

        if (line.startsWith("[") && line.endsWith("]")) {
            current = mutableListOf<String>().also { sections[line.substring(1, line.length - 1)] = it }
        } else {
            current?.add(line)
        }
    }

    val tls = sections[SECTION_TLS].orEmpty()
    val appSection = sections.keys.firstOrNull { it !in setOf(SECTION_TLS, SECTION_ADDRESS, SECTION_DNS, SECTION_ROUTING) }

    return StatusSummary(
        tlsProtocol = tls.firstOrNull { it.startsWith("PROTOCOL: ") }?.removePrefix("PROTOCOL: "),
        cipherSuite = tls.firstOrNull { it.startsWith("SUITE: ") }?.removePrefix("SUITE: "),
        addresses = sections[SECTION_ADDRESS].orEmpty(),
        dnsServers = sections[SECTION_DNS].orEmpty().filter { it != DNS_NOT_SPECIFIED },
        routes = sections[SECTION_ROUTING].orEmpty(),
        appListType = appSection,
        apps = appSection?.let { sections[it] }.orEmpty(),
    )
}

internal enum class AppRule { ONLY, EXCEPT }

internal val StatusSummary.appRule: AppRule?
    get() = when (appListType) {
        LIST_TYPE_ALLOWED -> AppRule.ONLY
        LIST_TYPE_DISALLOWED -> AppRule.EXCEPT
        else -> null
    }
