package kittoku.osc.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kittoku.osc.preference.ACTIVE_PROFILE_KEY
import kittoku.osc.preference.PROFILE_KEY_HEADER
import kittoku.osc.preference.ProfileEntry
import kittoku.osc.preference.ProfileForm
import kittoku.osc.preference.ProfileFormError
import kittoku.osc.preference.ProfileStore
import kittoku.osc.preference.profileFormOf
import kittoku.osc.preference.validateProfileForm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn


internal data class ProfilesUiState(
    val entries: List<ProfileEntry>,
    val activeName: String?,
)

internal class ProfilesViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val store = ProfileStore(prefs)

    init {
        store.ensureInitialized()
    }

    val uiState: StateFlow<ProfilesUiState> = prefs
        .changes { it.startsWith(PROFILE_KEY_HEADER) || it == ACTIVE_PROFILE_KEY }
        .map { readState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), readState())

    private fun readState() = ProfilesUiState(store.entries, store.activeName)

    // The legacy settings screen edits the working keys directly; pick those edits up on return
    fun onResume() {
        store.ensureInitialized()
        store.captureActive()
    }

    fun activate(name: String): Boolean = store.activate(name)

    fun delete(name: String) = store.delete(name)

    fun duplicate(name: String): String? = store.duplicate(name)

    fun formFor(name: String?): ProfileForm = if (name == null) ProfileForm() else profileFormOf(name, store.read(name))

    fun save(originalName: String?, form: ProfileForm): ProfileFormError? {
        validateProfileForm(form, originalName, store.names)?.also { return it }
        store.save(originalName, form)
        return null
    }

    fun exportJson(name: String): String? {
        if (name == store.activeName) store.captureActive()
        return store.readRaw(name)
    }

    fun importJson(baseName: String, json: String): String? = store.importRaw(baseName, json)
}
