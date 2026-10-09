package kittoku.osc.preference

import android.content.SharedPreferences
import kittoku.osc.preference.accessor.getStringPrefValue
import kotlinx.serialization.json.Json


internal const val ACTIVE_PROFILE_KEY = "ACTIVE_PROFILE"
private const val DEFAULT_PROFILE_NAME = "Connection"

internal data class ProfileEntry(val name: String, val hostname: String)

// Profiles are JSON snapshots stored under PROFILE.<name>. The core only reads the working keys,
// so the active profile is kept in sync with them: activating imports a profile into the working
// keys, captureActive() writes the working keys back into the active profile.
internal class ProfileStore(private val prefs: SharedPreferences) {
    val names: List<String>
        get() = prefs.all.keys
            .filter { it.startsWith(PROFILE_KEY_HEADER) }
            .map { it.removePrefix(PROFILE_KEY_HEADER) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    val entries: List<ProfileEntry>
        get() = names.map { name ->
            ProfileEntry(name, read(name)?.stringSetting?.get(OscPrefKey.HOME_HOSTNAME.name).orEmpty())
        }

    val activeName: String?
        get() = prefs.getString(ACTIVE_PROFILE_KEY, null)?.takeIf { exists(it) }

    fun exists(name: String): Boolean = prefs.contains(keyOf(name))

    fun read(name: String): Profile? = readRaw(name)?.let { deserializeProfile(it) }

    fun readRaw(name: String): String? = prefs.getString(keyOf(name), null)

    // Called on every start: adopts the settings of older app versions as a profile
    fun ensureInitialized() {
        if (activeName != null) return

        val working = serializeProfile(prefs)
        val hostname = getStringPrefValue(OscPrefKey.HOME_HOSTNAME, prefs)
        val matching = names.firstOrNull { readRaw(it) == working }

        when {
            matching != null -> setActive(matching)
            hostname.isNotEmpty() -> uniqueName(hostname).also {
                writeRaw(it, working)
                setActive(it)
            }
            names.isNotEmpty() -> activate(names.first())
        }
    }

    fun captureActive() {
        activeName?.also { writeRaw(it, serializeProfile(prefs)) }
    }

    fun activate(name: String): Boolean {
        if (name == activeName) return true

        val profile = read(name) ?: return false
        captureActive()
        importProfile(profile, prefs)
        setActive(name)

        return true
    }

    fun delete(name: String) {
        val wasActive = name == activeName
        prefs.edit().remove(keyOf(name)).apply()
        if (!wasActive) return

        prefs.edit().remove(ACTIVE_PROFILE_KEY).apply()
        val next = names.firstOrNull()
        if (next != null) {
            importProfile(read(next), prefs)
            setActive(next)
        } else {
            importProfile(null, prefs)
        }
    }

    fun duplicate(name: String): String? {
        if (name == activeName) captureActive()

        val raw = readRaw(name) ?: return null
        return uniqueName(name).also { writeRaw(it, raw) }
    }

    fun importRaw(baseName: String, json: String): String? {
        deserializeProfile(json) ?: return null

        val name = uniqueName(baseName)
        writeRaw(name, json)
        if (activeName == null) activate(name)

        return name
    }

    fun save(originalName: String?, form: ProfileForm): String {
        captureActive()

        val profile = originalName?.let { read(it) } ?: Profile()
        form.applyTo(profile)

        val name = form.effectiveName()
        val wasActive = originalName != null && originalName == activeName
        if (originalName != null && originalName != name) {
            prefs.edit().remove(keyOf(originalName)).apply()
        }
        writeRaw(name, Json.encodeToString(profile))

        when {
            wasActive -> {
                setActive(name)
                importProfile(profile, prefs)
            }
            activeName == null -> activate(name)
        }

        return name
    }

    fun uniqueName(base: String): String {
        val trimmed = base.trim().ifEmpty { DEFAULT_PROFILE_NAME }
        if (!exists(trimmed)) return trimmed

        return generateSequence(2) { it + 1 }.map { "$trimmed ($it)" }.first { !exists(it) }
    }

    private fun keyOf(name: String) = PROFILE_KEY_HEADER + name

    private fun writeRaw(name: String, json: String) {
        prefs.edit().putString(keyOf(name), json).apply()
    }

    private fun setActive(name: String) {
        prefs.edit().putString(ACTIVE_PROFILE_KEY, name).apply()
    }
}
