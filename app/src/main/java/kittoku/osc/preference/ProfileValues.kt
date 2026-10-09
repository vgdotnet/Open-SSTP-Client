package kittoku.osc.preference


internal fun Profile.getBoolean(key: OscPrefKey): Boolean = booleanSetting[key.name] ?: DEFAULT_BOOLEAN_MAP.getValue(key)

internal fun Profile.getInt(key: OscPrefKey): Int = intSetting[key.name] ?: DEFAULT_INT_MAP.getValue(key)

internal fun Profile.getString(key: OscPrefKey): String = stringSetting[key.name] ?: DEFAULT_STRING_MAP.getValue(key)

internal fun Profile.getSet(key: OscPrefKey): Set<String> = setSetting[key.name] ?: DEFAULT_SET_MAP.getValue(key)

internal fun Profile.getUri(key: OscPrefKey): String = uriSetting[key.name].orEmpty()

internal fun Profile.setBoolean(key: OscPrefKey, value: Boolean) {
    booleanSetting[key.name] = value
}

internal fun Profile.setInt(key: OscPrefKey, value: Int) {
    intSetting[key.name] = value
}

internal fun Profile.setString(key: OscPrefKey, value: String) {
    stringSetting[key.name] = value
}

internal fun Profile.setSet(key: OscPrefKey, value: Set<String>) {
    setSetting[key.name] = value
}

internal fun Profile.setUri(key: OscPrefKey, value: String?) {
    if (value.isNullOrEmpty()) uriSetting.remove(key.name) else uriSetting[key.name] = value
}
