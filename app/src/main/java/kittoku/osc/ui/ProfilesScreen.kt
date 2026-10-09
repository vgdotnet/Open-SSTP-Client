package kittoku.osc.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kittoku.osc.R
import kittoku.osc.preference.ProfileEntry


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfilesScreen(
    uiState: ProfilesUiState,
    canSwitch: Boolean,
    onBack: () -> Unit,
    onActivate: (String) -> Boolean,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    exportJson: (String) -> String?,
    importJson: (String, String) -> String?,
) {
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingExportConfirm by remember { mutableStateOf<String?>(null) }
    var exportingName by remember { mutableStateOf<String?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val name = exportingName
        exportingName = null
        if (uri == null || name == null) return@rememberLauncherForActivityResult

        val json = exportJson(name) ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        toast(context.getString(R.string.profiles_exported))
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        val json = context.contentResolver.openInputStream(uri)?.use { it.reader(Charsets.UTF_8).readText() }
        val baseName = DocumentFile.fromSingleUri(context, uri)?.name?.removeSuffix(".json").orEmpty()
        val imported = json?.let { importJson(baseName, it) }

        toast(
            if (imported == null) context.getString(R.string.profiles_import_failed)
            else context.getString(R.string.profiles_imported, imported)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profiles_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = stringResource(R.string.profiles_import))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.profiles_add))
            }
        },
    ) { innerPadding ->
        if (uiState.entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.profiles_empty), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onCreate) { Text(stringResource(R.string.profile_create)) }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(uiState.entries, key = { it.name }) { entry ->
                    ProfileRow(
                        entry = entry,
                        isActive = entry.name == uiState.activeName,
                        onClick = {
                            if (entry.name != uiState.activeName) {
                                if (canSwitch) onActivate(entry.name)
                                else toast(context.getString(R.string.profiles_switch_locked))
                            }
                        },
                        onEdit = { onEdit(entry.name) },
                        onDuplicate = { onDuplicate(entry.name) },
                        onExport = { pendingExportConfirm = entry.name },
                        onDelete = {
                            if (entry.name == uiState.activeName && !canSwitch) {
                                toast(context.getString(R.string.profiles_switch_locked))
                            } else {
                                pendingDelete = entry.name
                            }
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.also { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.profile_delete_title, name)) },
            text = { Text(stringResource(R.string.profile_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(name)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    pendingExportConfirm?.also { name ->
        AlertDialog(
            onDismissRequest = { pendingExportConfirm = null },
            title = { Text(stringResource(R.string.profile_export_title, name)) },
            text = { Text(stringResource(R.string.profile_export_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingExportConfirm = null
                    exportingName = name
                    exportLauncher.launch("$name.json")
                }) { Text(stringResource(R.string.action_export)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingExportConfirm = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ProfileRow(
    entry: ProfileEntry,
    isActive: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { RadioButton(selected = isActive, onClick = null) },
        headlineContent = { Text(entry.name) },
        supportingContent = { if (entry.hostname.isNotEmpty()) Text(entry.hostname) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_more))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    listOf(
                        R.string.profile_edit to onEdit,
                        R.string.profile_duplicate to onDuplicate,
                        R.string.profile_export to onExport,
                        R.string.profile_delete to onDelete,
                    ).forEach { (label, action) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = {
                                menuExpanded = false
                                action()
                            },
                        )
                    }
                }
            }
        },
    )
}
