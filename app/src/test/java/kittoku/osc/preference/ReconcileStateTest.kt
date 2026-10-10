package kittoku.osc.preference

import kittoku.osc.preference.accessor.getBooleanPrefValue
import kittoku.osc.preference.accessor.getConnectionState
import kittoku.osc.preference.accessor.getStringPrefValue
import kittoku.osc.preference.accessor.markConnected
import kittoku.osc.preference.accessor.markConnecting
import kittoku.osc.preference.accessor.markError
import kittoku.osc.preference.accessor.reconcileWithService
import kittoku.osc.preference.accessor.setBooleanPrefValue
import kittoku.osc.preference.accessor.setStringPrefValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class ReconcileStateTest {
    private val prefs = InMemorySharedPreferences()

    @Test
    fun connectedWithoutAServiceBecomesDisconnected() {
        // what an app update or a low-memory kill left behind on the phone
        markConnected(prefs)
        setBooleanPrefValue(true, OscPrefKey.ROOT_STATE, prefs)
        setStringPrefValue("[Assigned IP Address]\n192.168.2.2", OscPrefKey.HOME_STATUS, prefs)

        reconcileWithService(isServiceRunning = false, prefs)

        assertEquals(CONNECTION_STATE_DISCONNECTED, getConnectionState(prefs))
        assertFalse(getBooleanPrefValue(OscPrefKey.ROOT_STATE, prefs))
        assertEquals("", getStringPrefValue(OscPrefKey.HOME_STATUS, prefs))
    }

    @Test
    fun connectingWithoutAServiceBecomesDisconnected() {
        markConnecting(prefs)

        reconcileWithService(isServiceRunning = false, prefs)

        assertEquals(CONNECTION_STATE_DISCONNECTED, getConnectionState(prefs))
    }

    @Test
    fun runningServiceIsTrusted() {
        markConnected(prefs)
        setBooleanPrefValue(true, OscPrefKey.ROOT_STATE, prefs)

        reconcileWithService(isServiceRunning = true, prefs)

        assertEquals(CONNECTION_STATE_CONNECTED, getConnectionState(prefs))
        assertTrue(getBooleanPrefValue(OscPrefKey.ROOT_STATE, prefs))
    }

    @Test
    fun lastErrorIsKeptSoTheUserStillSeesWhatFailed() {
        markError("SSL: ERR_TIMEOUT", prefs)

        reconcileWithService(isServiceRunning = false, prefs)

        assertEquals(CONNECTION_STATE_ERROR, getConnectionState(prefs))
        assertEquals("SSL: ERR_TIMEOUT", getStringPrefValue(OscPrefKey.LAST_ERROR, prefs))
    }
}
