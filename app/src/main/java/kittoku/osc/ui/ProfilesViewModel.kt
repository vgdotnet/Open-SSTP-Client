package kittoku.osc.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kittoku.osc.preference.ACTIVE_PROFILE_KEY
import kittoku.osc.preference.PROFILE_KEY_HEADER
import kittoku.osc.preference.Profile
import kittoku.osc.preference.ProfileEntry
import kittoku.osc.preference.ProfileForm
import kittoku.osc.preference.ProfileFormError
import kittoku.osc.preference.ProfileStore
import kittoku.osc.preference.applyTo
import kittoku.osc.preference.deserializeProfile
import kittoku.osc.preference.effectiveName
import kittoku.osc.preference.profileFormOf
import kittoku.osc.preference.profileSettingsErrors
import kittoku.osc.preference.sanitizeProfile
import kittoku.osc.preference.validateProfileForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


private const val KEY_DRAFT = "draft"

internal data class ProfilesUiState(
    val entries: List<ProfileEntry>,
    val activeName: String?,
)

// The connection being edited: basic fields as typed text, everything else as profile JSON
@Serializable
internal data class ProfileDraft(
    val originalName: String?,
    val form: ProfileForm,
    val json: String,
    val attemptedSave: Boolean = false,
    val formError: ProfileFormError? = null,
) {
    fun profile(): Profile = deserializeProfile(json) ?: Profile()
}

internal class ProfilesViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private val store = ProfileStore(prefs)

    init {
        store.ensureInitialized()
    }

    val uiState: StateFlow<ProfilesUiState> = prefs
        .changes { it.startsWith(PROFILE_KEY_HEADER) || it == ACTIVE_PROFILE_KEY }
        .map { readState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), readState())

    private val mutableDraft = MutableStateFlow(
        savedState.get<String>(KEY_DRAFT)?.let { runCatching { Json.decodeFromString<ProfileDraft>(it) }.getOrNull() }
    )
    val draft: StateFlow<ProfileDraft?> = mutableDraft.asStateFlow()

    private fun readState() = ProfilesUiState(store.entries, store.activeName)

    // The legacy settings screen edits the working keys directly; pick those edits up on return
    fun onResume() {
        store.ensureInitialized()
        store.captureActive()
    }

    fun activate(name: String): Boolean = store.activate(name)

    fun delete(name: String) = store.delete(name)

    fun duplicate(name: String): String? = store.duplicate(name)

    fun exportJson(name: String): String? {
        if (name == store.activeName) store.captureActive()
        return store.readRaw(name)
    }

    fun importJson(baseName: String, json: String): String? = store.importRaw(baseName, json)

    fun startEdit(name: String?) {
        if (name != null && name == store.activeName) store.captureActive()

        val profile = name?.let { store.read(it) } ?: Profile()
        val form = if (name == null) ProfileForm() else profileFormOf(name, profile)
        setDraft(ProfileDraft(name, form, Json.encodeToString(profile)))
    }

    fun updateForm(form: ProfileForm) {
        mutableDraft.value?.also { setDraft(it.copy(form = form, formError = null)) }
    }

    fun updateProfile(edit: Profile.() -> Unit) {
        val draft = mutableDraft.value ?: return
        val profile = draft.profile().apply(edit)
        setDraft(draft.copy(json = Json.encodeToString(profile)))
    }

    fun discardDraft() = setDraft(null)

    fun saveDraft(): Boolean {
        val draft = mutableDraft.value ?: return false
        val profile = draft.profile()
        sanitizeProfile(profile)

        val formError = validateProfileForm(draft.form, draft.originalName, store.names)
        if (formError != null || profileSettingsErrors(profile).isNotEmpty()) {
            setDraft(draft.copy(json = Json.encodeToString(profile), attemptedSave = true, formError = formError))
            return false
        }

        draft.form.applyTo(profile)
        store.saveProfile(draft.originalName, draft.form.effectiveName(), profile)
        setDraft(null)
        return true
    }

    private fun setDraft(draft: ProfileDraft?) {
        mutableDraft.value = draft
        savedState[KEY_DRAFT] = draft?.let { Json.encodeToString(it) }
    }
}
