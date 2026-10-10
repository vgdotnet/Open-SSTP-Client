package kittoku.osc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kittoku.osc.R
import kittoku.osc.preference.LIST_TYPE_ALLOWED
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.Profile
import kittoku.osc.preference.ProfileForm
import kittoku.osc.preference.ProfileFormError
import kittoku.osc.preference.SettingsSection
import kittoku.osc.preference.getBoolean
import kittoku.osc.preference.getSet
import kittoku.osc.preference.getString
import kittoku.osc.preference.profileSettingsErrors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditScreen(
    draft: ProfileDraft,
    isLive: Boolean,
    onFormChange: (ProfileForm) -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val profile = remember(draft.json) { draft.profile() }
    val failedSections = remember(draft.json, draft.attemptedSave) {
        if (draft.attemptedSave) profileSettingsErrors(profile).map { it.section }.toSet() else emptySet()
    }

    var name by rememberSaveable { mutableStateOf(draft.form.name) }
    var hostname by rememberSaveable { mutableStateOf(draft.form.hostname) }
    var port by rememberSaveable { mutableStateOf(draft.form.port) }
    var username by rememberSaveable { mutableStateOf(draft.form.username) }
    var password by rememberSaveable { mutableStateOf(draft.form.password) }
    var camouflageSecret by rememberSaveable { mutableStateOf(draft.form.camouflageSecret) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    fun push() = onFormChange(ProfileForm(name, hostname, port, username, password, camouflageSecret))
    val error = draft.formError

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (draft.originalName == null) R.string.edit_title_new else R.string.edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isLive) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.edit_live_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                FormField(
                    value = name,
                    onValueChange = { name = it; push() },
                    label = R.string.field_name,
                    placeholder = R.string.field_name_hint,
                    supporting = if (error == ProfileFormError.NAME_TAKEN) R.string.error_name_taken else R.string.field_name_support,
                    isError = error == ProfileFormError.NAME_TAKEN,
                )
                FormField(
                    value = hostname,
                    onValueChange = { hostname = it; push() },
                    label = R.string.field_hostname,
                    placeholder = R.string.field_hostname_hint,
                    supporting = when (error) {
                        ProfileFormError.HOSTNAME_EMPTY -> R.string.error_hostname_empty
                        ProfileFormError.HOSTNAME_INVALID -> R.string.error_hostname_invalid
                        else -> null
                    },
                    isError = error == ProfileFormError.HOSTNAME_EMPTY || error == ProfileFormError.HOSTNAME_INVALID,
                    keyboardType = KeyboardType.Uri,
                )
                FormField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() }.take(5); push() },
                    label = R.string.field_port,
                    supporting = if (error == ProfileFormError.PORT_INVALID) R.string.error_port_invalid else null,
                    isError = error == ProfileFormError.PORT_INVALID,
                    keyboardType = KeyboardType.Number,
                )
                FormField(
                    value = username,
                    onValueChange = { username = it; push() },
                    label = R.string.field_username,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; push() },
                    label = { Text(stringResource(R.string.field_password)) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(if (passwordVisible) R.string.field_password_hide else R.string.field_password_show),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                FormField(
                    value = camouflageSecret,
                    onValueChange = { camouflageSecret = it; push() },
                    label = R.string.field_camouflage,
                    supporting = R.string.field_camouflage_support,
                )
            }

            SectionHeader(stringResource(R.string.edit_section_more))
            SectionRow(SettingsSection.ROUTES, R.string.section_routes, routesSummary(profile), failedSections, onOpenSection)
            SectionRow(SettingsSection.APPS, R.string.section_apps, appsSummary(profile), failedSections, onOpenSection)
            SectionRow(SettingsSection.ADVANCED, R.string.section_advanced, stringResource(R.string.summary_advanced), failedSections, onOpenSection)
        }
    }
}

@Composable
private fun SectionRow(
    section: SettingsSection,
    title: Int,
    summary: String,
    failedSections: Set<SettingsSection>,
    onOpen: (SettingsSection) -> Unit,
) {
    val failed = section in failedSections
    NavRow(
        title = stringResource(title),
        summary = if (failed) stringResource(R.string.summary_section_error) else summary,
        onClick = { onOpen(section) },
        isError = failed,
    )
}

@Composable
private fun routesSummary(profile: Profile): String {
    val traffic = when {
        profile.getBoolean(OscPrefKey.ROUTE_DO_ADD_DEFAULT_ROUTE) -> R.string.summary_route_all
        profile.getBoolean(OscPrefKey.ROUTE_DO_ROUTE_PRIVATE_ADDRESSES) || profile.getBoolean(OscPrefKey.ROUTE_DO_ADD_CUSTOM_ROUTES) -> R.string.summary_route_partial
        else -> R.string.summary_route_none
    }
    val dns = when {
        profile.getBoolean(OscPrefKey.DNS_DO_USE_CUSTOM_SERVER) -> stringResource(R.string.summary_dns_custom, profile.getString(OscPrefKey.DNS_CUSTOM_ADDRESS))
        profile.getBoolean(OscPrefKey.DNS_DO_REQUEST_ADDRESS) -> stringResource(R.string.summary_dns_server)
        else -> stringResource(R.string.summary_dns_system)
    }

    return stringResource(traffic) + " · " + dns
}

@Composable
private fun appsSummary(profile: Profile): String {
    val count = profile.getSet(OscPrefKey.ROUTE_SELECTED_APPS).size

    return when {
        !profile.getBoolean(OscPrefKey.ROUTE_DO_ENABLE_APP_BASED_RULE) -> stringResource(R.string.summary_apps_all)
        profile.getString(OscPrefKey.ROUTE_APP_LIST_TYPE) == LIST_TYPE_ALLOWED -> stringResource(R.string.summary_apps_only, count)
        else -> stringResource(R.string.summary_apps_except, count)
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    placeholder: Int? = null,
    supporting: Int? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        placeholder = placeholder?.let { { Text(stringResource(it)) } },
        supportingText = supporting?.let { { Text(stringResource(it)) } },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}
