package kittoku.osc.control

import java.util.concurrent.atomic.AtomicLong


// IP payload through the tunnel for the current session; the VPN service and the UI share the process
internal class TrafficCounter {
    private val received = AtomicLong()
    private val sent = AtomicLong()

    val receivedBytes: Long
        get() = received.get()

    val sentBytes: Long
        get() = sent.get()

    fun addReceived(bytes: Int) {
        received.addAndGet(bytes.toLong())
    }

    fun addSent(bytes: Int) {
        sent.addAndGet(bytes.toLong())
    }

    fun reset() {
        received.set(0)
        sent.set(0)
    }

    companion object {
        val shared = TrafficCounter()
    }
}
