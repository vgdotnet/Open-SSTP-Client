package kittoku.osc.preference


internal data class ProfileForm(
    val name: String = "",
    val hostname: String = "",
    val port: String = DEFAULT_INT_MAP.getValue(OscPrefKey.SSL_PORT).toString(),
    val username: String = "",
    val password: String = "",
    val camouflageSecret: String = "",
)

internal enum class ProfileFormError {
    HOSTNAME_EMPTY,
    HOSTNAME_INVALID,
    PORT_INVALID,
    NAME_TAKEN,
}

internal fun ProfileForm.effectiveName(): String = name.trim().ifEmpty { hostname.trim() }

internal fun profileFormOf(name: String, profile: Profile?): ProfileForm {
    fun string(key: OscPrefKey) = profile?.stringSetting?.get(key.name) ?: DEFAULT_STRING_MAP.getValue(key)
    val port = profile?.intSetting?.get(OscPrefKey.SSL_PORT.name) ?: DEFAULT_INT_MAP.getValue(OscPrefKey.SSL_PORT)

    return ProfileForm(
        name = name,
        hostname = string(OscPrefKey.HOME_HOSTNAME),
        port = port.toString(),
        username = string(OscPrefKey.HOME_USERNAME),
        password = string(OscPrefKey.HOME_PASSWORD),
        camouflageSecret = string(OscPrefKey.SSL_CAMOUFLAGE_SECRET),
    )
}

internal fun ProfileForm.applyTo(profile: Profile) {
    profile.stringSetting[OscPrefKey.HOME_HOSTNAME.name] = hostname.trim()
    profile.stringSetting[OscPrefKey.HOME_USERNAME.name] = username.trim()
    profile.stringSetting[OscPrefKey.HOME_PASSWORD.name] = password
    profile.stringSetting[OscPrefKey.SSL_CAMOUFLAGE_SECRET.name] = camouflageSecret.trim()
    profile.intSetting[OscPrefKey.SSL_PORT.name] = port.trim().toInt()
}

internal fun validateProfileForm(form: ProfileForm, originalName: String?, existingNames: Collection<String>): ProfileFormError? {
    val hostname = form.hostname.trim()
    if (hostname.isEmpty()) return ProfileFormError.HOSTNAME_EMPTY

    // the port has its own field, and a scheme or a path is a typical copy-paste mistake
    val looksLikeHostAndPort = hostname.count { it == ':' } == 1
    if ("://" in hostname || '/' in hostname || hostname.any { it.isWhitespace() } || looksLikeHostAndPort) {
        return ProfileFormError.HOSTNAME_INVALID
    }

    if (form.port.trim().toIntOrNull() !in 1..65535) return ProfileFormError.PORT_INVALID

    val name = form.effectiveName()
    if (name != originalName && name in existingNames) return ProfileFormError.NAME_TAKEN

    return null
}
