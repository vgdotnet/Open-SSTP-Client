package kittoku.osc.terminal

import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import kittoku.osc.ControlMessage
import kittoku.osc.Result
import kittoku.osc.SharedBridge
import kittoku.osc.Where
import kittoku.osc.extension.toHexByteArray
import kittoku.osc.preference.LIST_TYPE_ALLOWED
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.accessor.getBooleanPrefValue
import kittoku.osc.preference.accessor.getStringPrefValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.EOFException
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer


private const val MAX_EXCLUDED_ADDRESSES = 256
private const val RESOLVE_TIMEOUT = 2_000L
private const val POLL_INTERVAL = 500

private class TunInterface(val fd: ParcelFileDescriptor) {
    val input = FileInputStream(fd.fileDescriptor)
    val output = FileOutputStream(fd.fileDescriptor)

    fun close() {
        input.close()
        output.close()
        fd.close()
    }
}

internal class IPTerminal(private val bridge: SharedBridge) {
    // replaced as a whole when the interface is rebuilt; readers compare identity to notice the swap
    @Volatile
    private var tun: TunInterface? = null

    private val rebuildMutex = Mutex()
    private val excluded = linkedSetOf<InetAddress>()

    private val doEnableAppBasedRule = getBooleanPrefValue(OscPrefKey.ROUTE_DO_ENABLE_APP_BASED_RULE, bridge.prefs)
    private val isAllowedList = getStringPrefValue(OscPrefKey.ROUTE_APP_LIST_TYPE, bridge.prefs) == LIST_TYPE_ALLOWED
    private val doAddDefaultRoute = getBooleanPrefValue(OscPrefKey.ROUTE_DO_ADD_DEFAULT_ROUTE, bridge.prefs)
    private val doRoutePrivateAddresses = getBooleanPrefValue(OscPrefKey.ROUTE_DO_ROUTE_PRIVATE_ADDRESSES, bridge.prefs)
    private val doUseCustomDNSServer = getBooleanPrefValue(OscPrefKey.DNS_DO_USE_CUSTOM_SERVER, bridge.prefs)
    private val doAddCustomRoutes = getBooleanPrefValue(OscPrefKey.ROUTE_DO_ADD_CUSTOM_ROUTES, bridge.prefs)

    internal val canExcludeRoutes = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    internal suspend fun initialize() {
        if (bridge.PPP_IPv4_ENABLED && bridge.currentIPv4.contentEquals(ByteArray(4))) {
            bridge.controlMailbox.send(ControlMessage(Where.IPv4, Result.ERR_INVALID_ADDRESS))
            return
        }

        if (bridge.PPP_IPv6_ENABLED && bridge.currentIPv6.contentEquals(ByteArray(8))) {
            bridge.controlMailbox.send(ControlMessage(Where.IPv6, Result.ERR_INVALID_ADDRESS))
            return
        }

        if (doAddCustomRoutes && !areCustomRoutesValid()) {
            bridge.controlMailbox.send(ControlMessage(Where.ROUTE, Result.ERR_PARSING_FAILED))
            return
        }

        bridge.domainBypass?.also { bypass ->
            // the system DNS cache may already know these names, so their current addresses are excluded up front
            val resolved = resolveDomains(bypass.domains)
            synchronized(excluded) { excluded.addAll(resolved.filter { isExcludable(it) }.take(MAX_EXCLUDED_ADDRESSES)) }
        }

        rebuildMutex.withLock {
            tun = TunInterface(buildInterface().establish()!!)
        }

        bridge.controlMailbox.send(ControlMessage(Where.IP, Result.PROCEEDED))
    }

    private fun buildInterface(): VpnService.Builder {
        val builder = bridge.service.Builder()

        if (bridge.PPP_IPv4_ENABLED) {
            builder.addAddress(InetAddress.getByAddress(bridge.currentIPv4), 32)

            if (doUseCustomDNSServer) {
                builder.addDnsServer(getStringPrefValue(OscPrefKey.DNS_CUSTOM_ADDRESS, bridge.prefs))
            }

            if (!bridge.currentProposedDNS.contentEquals(ByteArray(4))) {
                builder.addDnsServer(InetAddress.getByAddress(bridge.currentProposedDNS))
            }

            if (doAddDefaultRoute) {
                builder.addRoute("0.0.0.0", 0)
            }

            if (doRoutePrivateAddresses) {
                builder.addRoute("10.0.0.0", 8)
                builder.addRoute("172.16.0.0", 12)
                builder.addRoute("192.168.0.0", 16)
            }
        }

        if (bridge.PPP_IPv6_ENABLED) {
            ByteArray(16).also { // for link local addresses
                "FE80".toHexByteArray().copyInto(it)
                ByteArray(6).copyInto(it, destinationOffset = 2)
                bridge.currentIPv6.copyInto(it, destinationOffset = 8)
                builder.addAddress(InetAddress.getByAddress(it), 64)
            }

            if (doAddDefaultRoute) {
                builder.addRoute("::", 0)
            }

            if (doRoutePrivateAddresses) {
                builder.addRoute("fc00::", 7)
            }
        }

        if (doAddCustomRoutes) {
            customRoutes().forEach { (address, prefix) -> builder.addRoute(address, prefix) }
        }

        if (doEnableAppBasedRule) {
            bridge.selectedApps.forEach {
                if (isAllowedList) {
                    builder.addAllowedApplication(it.packageName)
                } else {
                    builder.addDisallowedApplication(it.packageName)
                }
            }
        }

        if (canExcludeRoutes) {
            synchronized(excluded) { excluded.toList() }.forEach {
                builder.excludeRoute(IpPrefix(it, if (it is Inet6Address) 128 else 32))
            }
        }

        builder.setMtu(bridge.PPP_MTU)
        builder.setBlocking(true)

        return builder
    }

    private fun customRoutes(): List<Pair<String, Int>> {
        return getStringPrefValue(OscPrefKey.ROUTE_CUSTOM_ROUTES, bridge.prefs).split("\n").filter { it.isNotEmpty() }.map {
            val parsed = it.split("/")
            parsed[0] to parsed[1].toInt()
        }
    }

    private fun areCustomRoutesValid(): Boolean {
        return try {
            val probe = bridge.service.Builder()
            customRoutes().forEach { (address, prefix) -> probe.addRoute(address, prefix) }
            true
        } catch (_: Exception) { // malformed line, or an address the system rejects
            false
        }
    }

    private suspend fun resolveDomains(domains: List<String>): List<InetAddress> {
        return domains.flatMap { domain ->
            withTimeoutOrNull(RESOLVE_TIMEOUT) {
                runInterruptible(Dispatchers.IO) {
                    runCatching { InetAddress.getAllByName(domain).toList() }.getOrDefault(emptyList())
                }
            } ?: emptyList()
        }
    }

    private fun isExcludable(address: InetAddress): Boolean {
        return when (address) {
            is Inet4Address -> bridge.PPP_IPv4_ENABLED
            is Inet6Address -> bridge.PPP_IPv6_ENABLED
            else -> false
        }
    }

    internal fun isExcluded(address: InetAddress): Boolean {
        return synchronized(excluded) { address in excluded }
    }

    // Rebuilds the interface with the new exclusions; returns how many addresses were added
    internal suspend fun excludeAddresses(addresses: List<InetAddress>): Int {
        if (!canExcludeRoutes) return 0

        val fresh = synchronized(excluded) {
            addresses.filter { isExcludable(it) && it !in excluded }.take(MAX_EXCLUDED_ADDRESSES - excluded.size)
        }
        if (fresh.isEmpty()) return 0

        rebuildMutex.withLock {
            val old = tun ?: return 0 // not established yet: initialize() picks the set up
            synchronized(excluded) { excluded.addAll(fresh) }

            val fd = buildInterface().establish() ?: throw IllegalStateException("VPN permission revoked")
            tun = TunInterface(fd)
            old.close()
        }

        return fresh.size
    }

    internal fun writePacket(start: Int, size: Int, buffer: ByteBuffer) {
        writePacket(buffer.array(), start, size)
    }

    internal fun writePacket(packet: ByteArray, start: Int = 0, size: Int = packet.size) {
        // nothing will be written until initialized; a rebuild in between is retried on the new interface
        repeat(2) {
            val current = tun ?: return
            try {
                current.output.write(packet, start, size)
                return
            } catch (e: IOException) {
                if (current === tun) throw e
            }
        }
    }

    internal fun readPacket(buffer: ByteBuffer) {
        buffer.clear()

        while (true) {
            val current = tun
            if (current == null) {
                buffer.flip()
                return
            }

            // poll instead of a blocking read: a read stuck on a replaced interface would never return
            val pollFd = StructPollfd().also {
                it.fd = current.fd.fileDescriptor
                it.events = OsConstants.POLLIN.toShort()
            }
            val ready = try {
                Os.poll(arrayOf(pollFd), POLL_INTERVAL)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EINTR) 0 else throw e
            }
            if (ready <= 0 || current !== tun) continue

            val read = try {
                current.input.read(buffer.array(), 0, bridge.PPP_MTU)
            } catch (e: IOException) {
                if (current !== tun) continue else throw e
            }
            if (read < 0) {
                if (current !== tun) continue else throw EOFException("The VPN interface was closed")
            }

            buffer.position(read)
            buffer.flip()
            return
        }
    }

    internal fun close() {
        tun?.close()
    }
}
