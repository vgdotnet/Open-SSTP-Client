package kittoku.osc.preference

import android.content.SharedPreferences


internal enum class ReadinessError {
    NO_SERVER,
    INVALID_SETTINGS,
}

// Checked right before the VPN service is started, from the app and from the quick settings tile
internal fun connectionReadiness(prefs: SharedPreferences): ReadinessError? {
    val profile = deserializeProfile(serializeProfile(prefs)) ?: return ReadinessError.INVALID_SETTINGS
    if (profile.getString(OscPrefKey.HOME_HOSTNAME).isBlank()) return ReadinessError.NO_SERVER

    // the copy is sanitized like the editor does, so values of switched-off features do not count
    sanitizeProfile(profile)
    if (profile.getInt(OscPrefKey.SSL_PORT) !in PORT_RANGE) return ReadinessError.INVALID_SETTINGS
    if (profileSettingsErrors(profile).isNotEmpty()) return ReadinessError.INVALID_SETTINGS

    return null
}
