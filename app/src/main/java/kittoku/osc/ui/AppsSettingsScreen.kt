package kittoku.osc.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kittoku.osc.R
import kittoku.osc.preference.LIST_TYPE_ALLOWED
import kittoku.osc.preference.LIST_TYPE_DISALLOWED
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.Profile
import kittoku.osc.preference.SettingsError
import kittoku.osc.preference.getBoolean
import kittoku.osc.preference.getInstalledAppInfos
import kittoku.osc.preference.getSet
import kittoku.osc.preference.getString
import kittoku.osc.preference.setBoolean
import kittoku.osc.preference.setSet
import kittoku.osc.preference.setString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


private enum class AppsMode { ALL, ONLY, EXCEPT }

private data class AppItem(val packageName: String, val label: String, val info: ApplicationInfo)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppsSettingsScreen(profile: Profile, errors: Set<SettingsError>, onEdit: ProfileEditor, onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager

    val mode = when {
        !profile.getBoolean(OscPrefKey.ROUTE_DO_ENABLE_APP_BASED_RULE) -> AppsMode.ALL
        profile.getString(OscPrefKey.ROUTE_APP_LIST_TYPE) == LIST_TYPE_ALLOWED -> AppsMode.ONLY
        else -> AppsMode.EXCEPT
    }
    val showSystem = profile.getBoolean(OscPrefKey.ROUTE_DO_SHOW_BACKGROUND_APPS)
    val selected = profile.getSet(OscPrefKey.ROUTE_SELECTED_APPS)
    // sort by the selection at entry, so rows do not jump while ticking
    val selectedAtEntry = remember { selected }
    var query by rememberSaveable { mutableStateOf("") }

    val apps by produceState<List<AppItem>?>(null, showSystem) {
        value = withContext(Dispatchers.IO) {
            getInstalledAppInfos(showSystem, pm)
                .filter { it.packageName != context.packageName }
                .map { AppItem(it.packageName, pm.getApplicationLabel(it).toString(), it) }
        }
    }
    val shown = remember(apps, query) {
        apps
            ?.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
            ?.sortedWith(compareBy({ it.packageName !in selectedAtEntry }, { it.label.lowercase() }))
    }

    fun setMode(newMode: AppsMode) = onEdit {
        setBoolean(OscPrefKey.ROUTE_DO_ENABLE_APP_BASED_RULE, newMode != AppsMode.ALL)
        when (newMode) {
            AppsMode.ONLY -> setString(OscPrefKey.ROUTE_APP_LIST_TYPE, LIST_TYPE_ALLOWED)
            AppsMode.EXCEPT -> setString(OscPrefKey.ROUTE_APP_LIST_TYPE, LIST_TYPE_DISALLOWED)
            AppsMode.ALL -> {}
        }
    }

    Scaffold(topBar = { BackTopBar(R.string.section_apps, onBack) }) { innerPadding ->
        LazyColumn(contentPadding = innerPadding, modifier = Modifier.imePadding()) {
            item {
                ModeRow(mode == AppsMode.ALL, R.string.apps_mode_all, R.string.apps_mode_all_support) { setMode(AppsMode.ALL) }
                ModeRow(mode == AppsMode.ONLY, R.string.apps_mode_only, R.string.apps_mode_only_support) { setMode(AppsMode.ONLY) }
                ModeRow(mode == AppsMode.EXCEPT, R.string.apps_mode_except, R.string.apps_mode_except_support) { setMode(AppsMode.EXCEPT) }
                if (SettingsError.APPS_ONLY_EMPTY in errors) ErrorText(stringResource(R.string.error_apps_only_empty))
            }

            if (mode == AppsMode.ONLY && !profile.getBoolean(OscPrefKey.ROUTE_DO_ADD_DEFAULT_ROUTE)) {
                item {
                    Text(
                        text = stringResource(R.string.apps_route_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            if (mode != AppsMode.ALL) {
                item {
                    ProfileSwitch(profile, OscPrefKey.ROUTE_DO_SHOW_BACKGROUND_APPS, onEdit, R.string.apps_show_system)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.apps_search)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                val list = shown
                if (list == null) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(list, key = { it.packageName }) { app ->
                        AppRow(app, pm, checked = app.packageName in selected) { checked ->
                            onEdit {
                                val current = getSet(OscPrefKey.ROUTE_SELECTED_APPS)
                                setSet(OscPrefKey.ROUTE_SELECTED_APPS, if (checked) current + app.packageName else current - app.packageName)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeRow(selected: Boolean, title: Int, supporting: Int, onClick: () -> Unit) {
    ListItem(
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(supporting)) },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

@Composable
private fun AppRow(app: AppItem, pm: PackageManager, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { pm.getApplicationIcon(app.info).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }

    ListItem(
        leadingContent = {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) } ?: Spacer(Modifier.size(40.dp))
        },
        headlineContent = { Text(app.label) },
        supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    )
}
