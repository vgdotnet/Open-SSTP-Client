package kittoku.osc.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.accessor.getBooleanPrefValue
import kittoku.osc.preference.accessor.reconcileWithService
import kittoku.osc.preference.connectionReadiness
import kittoku.osc.preference.isInstalled
import kittoku.osc.ui.AppActivity


@RequiresApi(Build.VERSION_CODES.N)
internal class SstpTileService : TileService() {
    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    private val listener by lazy {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == OscPrefKey.ROOT_STATE.name) {
                updateTileState()
            }
        }
    }

    private val rootState: Boolean
        get() = getBooleanPrefValue(OscPrefKey.ROOT_STATE, prefs)

    private val isVpnPrepared: Boolean
        get() = VpnService.prepare(this) == null


    private fun updateTileState() {
        qsTile.state = if (rootState) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }

        qsTile.updateTile()
    }

    private fun flipTileState() {
        qsTile.state = if (qsTile.state == Tile.STATE_ACTIVE) {
            Tile.STATE_INACTIVE
        } else {
            Tile.STATE_ACTIVE
        }

        qsTile.updateTile()
    }

    // an unavailable tile cannot be clicked, so the app could never be opened from it to grant the VPN permission
    private fun initializeState() {
        updateTileState()
    }

    override fun onTileAdded() {
        requestListeningState(this, ComponentName(this, SstpTileService::class.java))
    }

    override fun onStartListening() {
        reconcileWithService(SstpVpnService.isRunning, prefs)
        initializeState()

        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onStopListening() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun startVpnService(action: String) {
        val intent = Intent(this, SstpVpnService::class.java).setAction(action)

        if (action == ACTION_VPN_CONNECT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun openApp() {
        val intent = Intent(this, AppActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onClick() {
        if (!rootState && (!isVpnPrepared || connectionReadiness(prefs) { packageManager.isInstalled(it) } != null)) {
            openApp()
            return
        }

        flipTileState()

        when (qsTile.state) {
            Tile.STATE_ACTIVE -> startVpnService(ACTION_VPN_CONNECT)
            Tile.STATE_INACTIVE -> startVpnService(ACTION_VPN_DISCONNECT)
        }
    }
}
