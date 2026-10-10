package kittoku.osc.control

import kittoku.osc.SharedBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetAddress


private const val BATCH_DELAY = 150L

// Watches DNS answers coming through the tunnel; addresses of bypassed domains are excluded from the VPN
// before the answer reaches the app, so the app's first connection already goes around the tunnel
internal class DomainBypass(private val bridge: SharedBridge, internal val domains: List<String>) {
    private val lock = Any()
    private val heldPackets = mutableListOf<ByteArray>()
    private val pendingAddresses = linkedSetOf<InetAddress>()
    private var pendingDomains = linkedSetOf<String>()
    private var flushJob: Job? = null

    // Returns true when the packet was taken and will be delivered after the interface is rebuilt
    fun intercept(packet: ByteArray, start: Int, size: Int): Boolean {
        val terminal = bridge.ipTerminal ?: return false
        val range = dnsPayloadRange(packet, start, size) ?: return false
        val response = parseDnsResponse(packet, range) ?: return false
        val decision = decideBypass(response, domains) { terminal.isExcluded(it) } ?: return false

        synchronized(lock) {
            heldPackets.add(packet.copyOfRange(start, start + size))
            pendingAddresses.addAll(decision.addresses)
            pendingDomains.add(decision.matchedDomain)

            if (flushJob == null) {
                flushJob = bridge.service.scope.launch { flush() }
            }
        }

        return true
    }

    private suspend fun flush() {
        delay(BATCH_DELAY)

        val packets: List<ByteArray>
        val addresses: List<InetAddress>
        val matched: Set<String>
        synchronized(lock) {
            packets = heldPackets.toList()
            addresses = pendingAddresses.toList()
            matched = pendingDomains
            heldPackets.clear()
            pendingAddresses.clear()
            pendingDomains = linkedSetOf()
        }

        try {
            val added = bridge.ipTerminal?.excludeAddresses(addresses) ?: 0
            bridge.service.logWriter.report(
                "Bypass: ${matched.joinToString()} -> $added address(es) excluded, ${addresses.size - added} skipped"
            )
        } catch (e: Exception) {
            bridge.service.logWriter.report("Bypass: interface rebuild failed, ${e}", isError = true)
        } finally {
            // the tunnel may be gone by now (disconnect while holding); the loop that owns the socket reports that
            packets.forEach { packet -> runCatching { bridge.ipTerminal?.writePacket(packet) } }

            synchronized(lock) {
                flushJob = if (heldPackets.isEmpty()) null else bridge.service.scope.launch { flush() }
            }
        }
    }
}
