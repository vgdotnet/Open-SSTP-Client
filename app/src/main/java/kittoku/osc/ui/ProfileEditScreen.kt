package kittoku.osc.ui

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kittoku.osc.R
import kittoku.osc.preference.ProfileForm
import kittoku.osc.preference.ProfileFormError


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditScreen(
    isNew: Boolean,
    initial: ProfileForm,
    onSave: (ProfileForm) -> ProfileFormError?,
    onBack: () -> Unit,
    onOpenLegacySettings: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var hostname by rememberSaveable { mutableStateOf(initial.hostname) }
    var port by rememberSaveable { mutableStateOf(initial.port) }
    var username by rememberSaveable { mutableStateOf(initial.username) }
    var password by rememberSaveable { mutableStateOf(initial.password) }
    var camouflageSecret by rememberSaveable { mutableStateOf(initial.camouflageSecret) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<ProfileFormError?>(null) }

    val save = {
        error = onSave(ProfileForm(name, hostname, port, username, password, camouflageSecret))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.edit_title_new else R.string.edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = save) { Text(stringResource(R.string.action_save)) }
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormField(
                value = name,
                onValueChange = { name = it; error = null },
                label = R.string.field_name,
                placeholder = R.string.field_name_hint,
                supporting = if (error == ProfileFormError.NAME_TAKEN) R.string.error_name_taken else R.string.field_name_support,
                isError = error == ProfileFormError.NAME_TAKEN,
            )
            FormField(
                value = hostname,
                onValueChange = { hostname = it; error = null },
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
                onValueChange = { port = it.filter { c -> c.isDigit() }.take(5); error = null },
                label = R.string.field_port,
                supporting = if (error == ProfileFormError.PORT_INVALID) R.string.error_port_invalid else null,
                isError = error == ProfileFormError.PORT_INVALID,
                keyboardType = KeyboardType.Number,
            )
            FormField(
                value = username,
                onValueChange = { username = it },
                label = R.string.field_username,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
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
                onValueChange = { camouflageSecret = it },
                label = R.string.field_camouflage,
                supporting = R.string.field_camouflage_support,
            )

            Text(
                text = stringResource(R.string.edit_legacy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onOpenLegacySettings) { Text(stringResource(R.string.menu_legacy_settings)) }
        }
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
