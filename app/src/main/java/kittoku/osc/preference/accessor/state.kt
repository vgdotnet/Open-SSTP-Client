package kittoku.osc.preference.accessor

import android.content.SharedPreferences
import kittoku.osc.preference.CONNECTION_STATE_CONNECTED
import kittoku.osc.preference.CONNECTION_STATE_CONNECTING
import kittoku.osc.preference.CONNECTION_STATE_DISCONNECTED
import kittoku.osc.preference.CONNECTION_STATE_ERROR
import kittoku.osc.preference.OscPrefKey


internal fun getConnectionState(prefs: SharedPreferences): String {
    return getStringPrefValue(OscPrefKey.CONNECTION_STATE, prefs)
}

internal fun setConnectionState(state: String, prefs: SharedPreferences) {
    setStringPrefValue(state, OscPrefKey.CONNECTION_STATE, prefs)
}

internal fun markConnecting(prefs: SharedPreferences) {
    setStringPrefValue("", OscPrefKey.LAST_ERROR, prefs)
    setConnectionState(CONNECTION_STATE_CONNECTING, prefs)
}

internal fun markConnected(prefs: SharedPreferences) {
    setStringPrefValue(System.currentTimeMillis().toString(), OscPrefKey.CONNECTED_SINCE, prefs)
    setConnectionState(CONNECTION_STATE_CONNECTED, prefs)
}

internal fun markDisconnected(prefs: SharedPreferences) {
    setConnectionState(CONNECTION_STATE_DISCONNECTED, prefs)
}

internal fun markError(message: String, prefs: SharedPreferences) {
    setStringPrefValue(message, OscPrefKey.LAST_ERROR, prefs)
    setConnectionState(CONNECTION_STATE_ERROR, prefs)
}

internal fun markDisconnectedUnlessError(prefs: SharedPreferences) {
    if (getConnectionState(prefs) != CONNECTION_STATE_ERROR) {
        markDisconnected(prefs)
    }
}

// The state is persisted, the service is not: after the process was killed (update, low memory)
// a "connected" state would stay forever. Called whenever the UI or the tile comes up.
internal fun reconcileWithService(isServiceRunning: Boolean, prefs: SharedPreferences) {
    if (isServiceRunning) return

    val state = getConnectionState(prefs)
    if (state == CONNECTION_STATE_CONNECTING || state == CONNECTION_STATE_CONNECTED) {
        setStringPrefValue("", OscPrefKey.HOME_STATUS, prefs)
        markDisconnected(prefs)
    }

    if (getBooleanPrefValue(OscPrefKey.ROOT_STATE, prefs)) {
        setBooleanPrefValue(false, OscPrefKey.ROOT_STATE, prefs)
    }
}
