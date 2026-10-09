package kittoku.osc.control

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream


class ConnectionLogTest {
    @Test
    fun keepsOnlyTheLatestLinesInOrder() {
        val log = ConnectionLog(capacity = 3)

        (1..5).forEach { log.append(LogLine(it.toLong(), false, "line $it")) }

        assertEquals(listOf("line 3", "line 4", "line 5"), log.snapshot.value.map { it.text })
    }

    @Test
    fun clearEmptiesTheSnapshot() {
        val log = ConnectionLog(capacity = 3)
        log.append(LogLine(1, false, "line"))

        log.clear()

        assertTrue(log.snapshot.value.isEmpty())
    }

    @Test
    fun writerFeedsTheBufferAndTheFile() {
        val log = ConnectionLog(capacity = 10)
        val file = ByteArrayOutputStream()
        val writer = LogWriter(log, file)

        writer.write("Establish VPN connection")
        runBlocking { writer.report("SSL: ERR_TIMEOUT", isError = true) }
        writer.close()

        assertEquals(listOf("Establish VPN connection", "SSL: ERR_TIMEOUT"), log.snapshot.value.map { it.text })
        assertFalse(log.snapshot.value[0].isError)
        assertTrue(log.snapshot.value[1].isError)

        val fileLines = file.toString("UTF-8").trimEnd().lines()
        assertEquals(2, fileLines.size)
        assertTrue(fileLines[0].matches(Regex("""\[\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}] Establish VPN connection""")))
        assertTrue(fileLines[1].endsWith("] SSL: ERR_TIMEOUT"))
    }

    @Test
    fun writerWorksWithoutAFile() {
        val log = ConnectionLog(capacity = 10)
        val writer = LogWriter(log, null)

        writer.write("no file")
        writer.close()

        assertEquals(listOf("no file"), log.snapshot.value.map { it.text })
    }
}
