package kittoku.osc.ui

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kittoku.osc.preference.CONNECTION_STATE_CONNECTED
import kittoku.osc.preference.CONNECTION_STATE_CONNECTING
import kittoku.osc.preference.CONNECTION_STATE_ERROR
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.accessor.getConnectionState
import kittoku.osc.preference.accessor.getStringPrefValue
import kittoku.osc.preference.checkPreferences
import kittoku.osc.service.ACTION_VPN_CONNECT
import kittoku.osc.service.ACTION_VPN_DISCONNECT
import kittoku.osc.service.SstpVpnService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn


internal enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

internal data class HomeUiState(
    val profileName: String,
    val state: ConnectionState,
    val connectedSince: Long?,
    val errorCode: String,
    val status: StatusSummary?,
    val rawStatus: String,
)

private val OBSERVED_KEYS = setOf(
    OscPrefKey.HOME_HOSTNAME,
    OscPrefKey.HOME_STATUS,
    OscPrefKey.CONNECTION_STATE,
    OscPrefKey.CONNECTED_SINCE,
    OscPrefKey.LAST_ERROR,
).map { it.name }

internal class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    val uiState: StateFlow<HomeUiState> = prefsChanges()
        .map { readState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), readState())

    private fun prefsChanges() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in OBSERVED_KEYS) trySend(Unit)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun readState(): HomeUiState {
        val rawStatus = getStringPrefValue(OscPrefKey.HOME_STATUS, prefs)

        return HomeUiState(
            profileName = getStringPrefValue(OscPrefKey.HOME_HOSTNAME, prefs),
            state = when (getConnectionState(prefs)) {
                CONNECTION_STATE_CONNECTING -> ConnectionState.CONNECTING
                CONNECTION_STATE_CONNECTED -> ConnectionState.CONNECTED
                CONNECTION_STATE_ERROR -> ConnectionState.ERROR
                else -> ConnectionState.DISCONNECTED
            },
            connectedSince = getStringPrefValue(OscPrefKey.CONNECTED_SINCE, prefs).toLongOrNull(),
            errorCode = getStringPrefValue(OscPrefKey.LAST_ERROR, prefs),
            status = parseStatusSummary(rawStatus),
            rawStatus = rawStatus,
        )
    }

    // Returns a validation message when the settings are not usable, null when the service was started
    fun connect(): String? {
        checkPreferences(prefs)?.also { return it }
        startService(ACTION_VPN_CONNECT)
        return null
    }

    fun disconnect() {
        startService(ACTION_VPN_DISCONNECT)
    }

    private fun startService(action: String) {
        val context = getApplication<Application>()
        val intent = Intent(context, SstpVpnService::class.java).setAction(action)

        if (action == ACTION_VPN_CONNECT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
