package kittoku.osc.control

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


internal data class LogLine(val timeMillis: Long, val isError: Boolean, val text: String)

// Ring buffer holding the log of the latest connection attempt; the VPN service and the UI share the process
internal class ConnectionLog(private val capacity: Int) {
    private val lines = ArrayDeque<LogLine>()
    private val state = MutableStateFlow<List<LogLine>>(emptyList())

    val snapshot: StateFlow<List<LogLine>> = state.asStateFlow()

    @Synchronized
    fun append(line: LogLine) {
        if (lines.size == capacity) lines.removeFirst()
        lines.addLast(line)
        state.value = lines.toList()
    }

    @Synchronized
    fun clear() {
        lines.clear()
        state.value = emptyList()
    }

    companion object {
        val shared = ConnectionLog(capacity = 500)
    }
}
