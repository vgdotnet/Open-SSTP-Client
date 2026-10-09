package kittoku.osc.control

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


internal class LogWriter(private val log: ConnectionLog, fileOutput: OutputStream?) {
    private val mutex = Mutex()
    private val outputStream = fileOutput?.let { BufferedOutputStream(it) }

    private fun formatTime(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(millis))
    }

    internal fun write(message: String, isError: Boolean = false) {
        // use directly if it's cannot be race condition, like onCreate and onDestroy
        val now = System.currentTimeMillis()
        log.append(LogLine(now, isError, message))
        outputStream?.write("[${formatTime(now)}] $message\n".toByteArray(Charsets.UTF_8))
    }

    internal suspend fun report(message: String, isError: Boolean = false) {
        mutex.withLock { write(message, isError) }
    }

    internal fun close() {
        outputStream?.flush()
        outputStream?.close()
    }
}
