package kittoku.osc.ui

import java.util.Locale


internal data class TrafficSnapshot(
    val receivedBytes: Long,
    val sentBytes: Long,
    val receiveRate: Long, // bytes per second
    val sendRate: Long,
) {
    companion object {
        val ZERO = TrafficSnapshot(0, 0, 0, 0)
    }
}

// Turns the running byte counters into totals and per-second rates between two samples
internal class TrafficSampler {
    private var lastTime = -1L
    private var lastReceived = 0L
    private var lastSent = 0L

    fun sample(nowMillis: Long, received: Long, sent: Long): TrafficSnapshot {
        val elapsed = nowMillis - lastTime
        // a counter going down means a new session started between the samples
        val isComparable = lastTime >= 0 && elapsed > 0 && received >= lastReceived && sent >= lastSent

        val receiveRate = if (isComparable) (received - lastReceived) * 1000 / elapsed else 0
        val sendRate = if (isComparable) (sent - lastSent) * 1000 / elapsed else 0

        lastTime = nowMillis
        lastReceived = received
        lastSent = sent

        return TrafficSnapshot(received, sent, receiveRate, sendRate)
    }
}

internal enum class SizeUnit { B, KB, MB, GB }

internal data class SizeText(val number: String, val unit: SizeUnit)

internal fun formatSize(bytes: Long, locale: Locale): SizeText {
    if (bytes < 1024) return SizeText(bytes.coerceAtLeast(0).toString(), SizeUnit.B)

    var value = bytes / 1024.0
    var unit = SizeUnit.KB
    while (value >= 1024 && unit != SizeUnit.GB) {
        value /= 1024
        unit = SizeUnit.entries[unit.ordinal + 1]
    }

    val number = if (value >= 100) String.format(locale, "%.0f", value) else String.format(locale, "%.1f", value)
    return SizeText(number, unit)
}
