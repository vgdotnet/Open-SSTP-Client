package kittoku.osc.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kittoku.osc.R
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.Profile
import kittoku.osc.preference.getBoolean
import kittoku.osc.preference.getInt
import kittoku.osc.preference.getString
import kittoku.osc.preference.setBoolean
import kittoku.osc.preference.setInt
import kittoku.osc.preference.setString


internal typealias ProfileEditor = (Profile.() -> Unit) -> Unit

// Keeps showing the last value while a screen animates out after its state was cleared
@Composable
internal fun <T : Any> rememberLastNonNull(value: T?): T? {
    val last = remember { mutableStateOf(value) }
    SideEffect { if (value != null) last.value = value }
    return value ?: last.value
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
internal fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
internal fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, supporting: String? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@Composable
internal fun ProfileSwitch(profile: Profile, key: OscPrefKey, onEdit: ProfileEditor, title: Int, supporting: Int? = null) {
    SwitchRow(
        title = stringResource(title),
        checked = profile.getBoolean(key),
        onCheckedChange = { value -> onEdit { setBoolean(key, value) } },
        supporting = supporting?.let { stringResource(it) },
    )
}

@Composable
internal fun NavRow(title: String, summary: String, onClick: () -> Unit, isError: Boolean = false) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(summary, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
internal fun FolderRow(title: String, uri: String, onPick: () -> Unit, isError: Boolean) {
    val path = uri.takeIf { it.isNotEmpty() }?.let { Uri.parse(it).path ?: it }
    NavRow(
        title = title,
        summary = path ?: stringResource(if (isError) R.string.error_folder_missing else R.string.folder_none),
        onClick = onPick,
        isError = isError,
    )
}

@Composable
internal fun SettingTextField(
    stateKey: String,
    initial: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    password: Boolean = false,
) {
    // local state keeps typing smooth; the draft is updated on every change
    var text by rememberSaveable(stateKey) { mutableStateOf(initial) }
    var visible by rememberSaveable(stateKey + "_visible") { mutableStateOf(false) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (password) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(if (visible) R.string.field_password_hide else R.string.field_password_show),
                    )
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            autoCorrectEnabled = false,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
internal fun ProfileTextField(
    profile: Profile,
    key: OscPrefKey,
    onEdit: ProfileEditor,
    label: Int,
    placeholder: String? = null,
    supporting: Int? = null,
    error: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    password: Boolean = false,
) {
    SettingTextField(
        stateKey = key.name,
        initial = profile.getString(key),
        onValueChange = { value -> onEdit { setString(key, value) } },
        label = stringResource(label),
        placeholder = placeholder,
        supporting = (error ?: supporting)?.let { stringResource(it) },
        isError = error != null,
        keyboardType = keyboardType,
        singleLine = singleLine,
        password = password,
    )
}

@Composable
internal fun ProfileIntField(profile: Profile, key: OscPrefKey, onEdit: ProfileEditor, label: Int, range: IntRange) {
    val initial = profile.getInt(key).let { if (it < 0) "" else it.toString() }
    var text by rememberSaveable(key.name) { mutableStateOf(initial) }
    val isValid = text.toIntOrNull()?.let { it in range } ?: false

    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value.filter { it.isDigit() }.take(6)
            onEdit { setInt(key, text.toIntOrNull() ?: -1) }
        },
        label = { Text(stringResource(label)) },
        supportingText = if (isValid) null else { { Text(stringResource(R.string.error_range, range.first, range.last)) } },
        isError = !isValid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoiceField(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    error: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            supportingText = error?.let { { Text(it) } },
            isError = error != null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackTopBar(title: Int, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
    )
}
