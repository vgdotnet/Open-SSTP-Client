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
import kittoku.osc.preference.ACTIVE_PROFILE_KEY
import kittoku.osc.preference.CONNECTION_STATE_ERROR
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.PROFILE_KEY_HEADER
import kittoku.osc.preference.ProfileStore
import kittoku.osc.preference.accessor.getConnectionState
import kittoku.osc.preference.accessor.reconcileWithService
import kittoku.osc.preference.accessor.getStringPrefValue
import kittoku.osc.preference.ReadinessError
import kittoku.osc.preference.connectionReadiness
import kittoku.osc.preference.isInstalled
import kittoku.osc.service.ACTION_VPN_CONNECT
import kittoku.osc.service.ACTION_VPN_DISCONNECT
import kittoku.osc.service.SstpVpnService
import android.os.SystemClock
import kittoku.osc.control.TrafficCounter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn


internal enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

internal data class HomeUiState(
    val profileName: String,
    val profileNames: List<String>,
    val state: ConnectionState,
    val connectedSince: Long?,
    val errorCode: String,
    val status: StatusSummary?,
    val rawStatus: String,
)

private val OBSERVED_KEYS = setOf(
    OscPrefKey.HOME_STATUS,
    OscPrefKey.CONNECTION_STATE,
    OscPrefKey.CONNECTED_SINCE,
    OscPrefKey.LAST_ERROR,
).map { it.name }

internal class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val profiles = ProfileStore(prefs)

    init {
        reconcileWithService(SstpVpnService.isRunning, prefs)
    }

    val uiState: StateFlow<HomeUiState> = prefs
        .changes { it in OBSERVED_KEYS || it == ACTIVE_PROFILE_KEY || it.startsWith(PROFILE_KEY_HEADER) }
        .map { readState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), readState())

    val traffic: StateFlow<TrafficSnapshot> = flow {
        val sampler = TrafficSampler()
        val counter = TrafficCounter.shared
        while (true) {
            emit(sampler.sample(SystemClock.elapsedRealtime(), counter.receivedBytes, counter.sentBytes))
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), TrafficSnapshot.ZERO)

    private fun readState(): HomeUiState {
        val rawStatus = getStringPrefValue(OscPrefKey.HOME_STATUS, prefs)

        return HomeUiState(
            profileName = profiles.activeName.orEmpty(),
            profileNames = profiles.names,
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

    // Returns why the active connection cannot be started, null when the service was started
    fun connect(): ReadinessError? {
        val pm = getApplication<Application>().packageManager
        connectionReadiness(prefs) { pm.isInstalled(it) }?.also { return it }
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
